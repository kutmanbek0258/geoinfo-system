import os
import shutil
import tempfile
import zipfile
import tarfile
import glob
from typing import Any, Dict, List, Optional, Tuple
from .base import BaseProcessor
from ..core.config import logger, GDAL_STORE
from ..core.clients import minio_client
from ..core.gdal import run_command, get_band_stats, safe_scale_range, build_final_cog
from ..registry import get_formula_and_bands, build_gdal_calc_formula

class Landsat8Processor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        characteristics = job_data.get("characteristics", {})

        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        index_type = characteristics.get("indexType")
        channels = characteristics.get("channels", [])

        if index_type:
            _, index_bands = get_formula_and_bands("landsat8", index_type)
            if index_bands:
                channels = index_bands

        if not channels:
            logger.error("No channels or index specified for Landsat 8 job %s", job_id)
            self.send_status(job_id, "FAILED", "LANDSAT_COG", error_message="No spectral channels specified", output_prefix=output_prefix)
            return

        render_mode = self.determine_render_mode(characteristics, index_type, channels)
        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]
        final_output_file = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))

        work_dir = tempfile.mkdtemp(prefix="landsat8-{0}-".format(job_id))
        archive_file = os.path.join(work_dir, "source.archive")
        extract_dir = os.path.join(work_dir, "extracted")

        try:
            self.send_status(job_id, "PROCESSING", "LANDSAT_COG", output_prefix=output_prefix)
            logger.info("Downloading Landsat 8 archive: %s/%s", source_bucket, source_key)
            minio_client.fget_object(source_bucket, source_key, archive_file)

            os.makedirs(extract_dir, exist_ok=True)
            if tarfile.is_tarfile(archive_file):
                with tarfile.open(archive_file, "r:*") as tar_ref:
                    tar_ref.extractall(extract_dir)
            elif zipfile.is_zipfile(archive_file):
                with zipfile.ZipFile(archive_file, "r") as zip_ref:
                    zip_ref.extractall(extract_dir)
            else:
                # Assume it's a single TIF file? Or unsupported.
                # Landsat 8 is usually a collection.
                raise RuntimeError("Unsupported archive format for Landsat 8")

            band_paths: List[str] = []
            for channel in channels:
                path = self.find_band_path(extract_dir, channel)
                if not path:
                    raise RuntimeError("Band {0} not found in Landsat 8 archive".format(channel))
                band_paths.append(path)

            processed_tif = os.path.join(work_dir, "processed.tif")

            if index_type:
                formula, expected = get_formula_and_bands("landsat8", index_type)
                if not formula:
                    raise RuntimeError("Unsupported index type: {0}".format(index_type))

                calc_cmd = ["gdal_calc.py"]
                for idx, path in enumerate(band_paths):
                    var = chr(ord("A") + idx)
                    calc_cmd.extend(["-{0}".format(var), path])

                gdal_formula = build_gdal_calc_formula(formula, expected)
                calc_cmd.extend([
                    "--calc", gdal_formula,
                    "--outfile", processed_tif,
                    "--NoDataValue", "-9999",
                    "--type", "Float32",
                    "--overwrite",
                ])
                run_command(calc_cmd)
            else:
                vrt_file = os.path.join(work_dir, "merged.vrt")
                run_command(["gdalbuildvrt", "-separate", vrt_file] + band_paths)
                processed_tif = vrt_file

            logger.info("Converting Landsat 8 to Cloud Optimized GeoTIFF: %s", final_output_file)

            if render_mode == "web_rgb" and len(band_paths) >= 3:
                scale_ranges: List[Tuple[float, float]] = []
                for p in band_paths[:3]:
                    band_min, band_max = get_band_stats(p, 1)
                    scale_ranges.append(safe_scale_range(band_min, band_max))
                build_final_cog(processed_tif, final_output_file, render_mode="web_rgb", scale_ranges=scale_ranges)
            elif index_type:
                build_final_cog(processed_tif, final_output_file, render_mode="analytic", is_float_index=True)
            else:
                build_final_cog(processed_tif, final_output_file, render_mode="analytic")

            self.send_status(job_id, "READY", "LANDSAT_COG", output_prefix=output_prefix)
            logger.info("Landsat 8 job %s completed successfully", job_id)

        except Exception as e:
            logger.exception("Landsat 8 job %s failed", job_id)
            self.send_status(job_id, "FAILED", "LANDSAT_COG", error_message=str(e), output_prefix=output_prefix)
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        if not output_prefix: return
        target = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))
        logger.info("Deleting Landsat 8 data at %s", target)
        try:
            if os.path.isfile(target):
                os.remove(target)
            for suffix in (".aux.xml", ".ovr", ".msk"):
                sidecar = target + suffix
                if os.path.isfile(sidecar):
                    os.remove(sidecar)
        except Exception:
            logger.exception("Failed to delete %s", target)

    def find_band_path(self, extract_dir: str, band: str) -> Optional[str]:
        # Landsat 8 files typically look like: LC08_L1TP_149039_20170411_20170414_01_T1_B1.TIF
        # We look for *_B{N}.TIF or *_B{N}.tif
        band_num = band.upper()
        if band_num.startswith("B0"):
            band_num = "B" + band_num[2:]

        patterns = [
            os.path.join(extract_dir, "**/*_{0}.TIF".format(band_num)),
            os.path.join(extract_dir, "**/*_{0}.tif".format(band_num)),
            os.path.join(extract_dir, "**/*_{0}_*.TIF".format(band_num)),
            os.path.join(extract_dir, "**/*_{0}_*.tif".format(band_num)),
        ]

        for pattern in patterns:
            matches = glob.glob(pattern, recursive=True)
            if matches:
                matches.sort()
                return matches[0]
        return None
