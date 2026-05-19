import os
import shutil
import tempfile
import zipfile
import glob
from typing import Any, Dict, List, Optional, Tuple
from .base import BaseProcessor
from ..core.config import logger, GDAL_STORE
from ..core.clients import minio_client
from ..core.gdal import run_command, get_band_stats, safe_scale_range, build_final_cog
from ..registry import SENTINEL2_INDEX_BANDS, build_gdal_calc_formula, get_formula_and_bands

class Sentinel2Processor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        characteristics = job_data.get("characteristics", {})

        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        index_type = characteristics.get("indexType")
        channels = characteristics.get("channels", [])

        if index_type:
            _, index_bands = get_formula_and_bands("sentinel2", index_type)
            if index_bands:
                channels = index_bands

        if not channels:
            logger.error("No channels or index specified for Sentinel-2 job %s", job_id)
            self.send_status(job_id, "FAILED", "SENTINEL_COG", error_message="No spectral channels specified", output_prefix=output_prefix)
            return

        render_mode = self.determine_render_mode(characteristics, index_type, channels)
        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]
        final_output_file = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))

        work_dir = tempfile.mkdtemp(prefix="sentinel-{0}-".format(job_id))
        zip_file = os.path.join(work_dir, "source.zip")
        extract_dir = os.path.join(work_dir, "extracted")

        try:
            self.send_status(job_id, "PROCESSING", "SENTINEL_COG", output_prefix=output_prefix)
            logger.info("Downloading Sentinel archive: %s/%s", source_bucket, source_key)
            minio_client.fget_object(source_bucket, source_key, zip_file)

            with zipfile.ZipFile(zip_file, "r") as zip_ref:
                zip_ref.extractall(extract_dir)

            normalized_paths: List[str] = []
            normalized_band_names: List[str] = []

            for channel in channels:
                src_path = self.find_channel_path(extract_dir, channel)
                if not src_path:
                    raise RuntimeError("Channel {0} not found in archive".format(channel))

                canonical = self.normalize_band_name(channel)
                norm_path = os.path.join(work_dir, "{0}_10m.tif".format(canonical))

                logger.info("Normalizing channel %s to 10m -> %s", channel, norm_path)
                run_command([
                    "gdalwarp",
                    "-overwrite",
                    "-tr", "10", "10",
                    "-tap",
                    "-r", "bilinear",
                    "-multi",
                    "-wo", "NUM_THREADS=ALL_CPUS",
                    "-srcnodata", "0",
                    "-dstnodata", "0",
                    src_path,
                    norm_path,
                ])

                normalized_paths.append(norm_path)
                normalized_band_names.append(canonical)

            processed_tif = os.path.join(work_dir, "processed.tif")

            if index_type:
                formula, expected = get_formula_and_bands("sentinel2", index_type)
                if not formula:
                    raise RuntimeError("Unsupported index type: {0}".format(index_type))

                path_by_band: Dict[str, str] = {}
                for band_name, band_path in zip(normalized_band_names, normalized_paths):
                    path_by_band[band_name] = band_path
                    path_by_band[band_name.replace("0", "")] = band_path

                calc_cmd = ["gdal_calc.py"]
                for idx, band in enumerate(expected):
                    var = chr(ord("A") + idx)
                    band_path = path_by_band.get(band) or path_by_band.get(band.replace("0", ""))
                    if not band_path:
                        raise RuntimeError("Required band {0} missing for {1}".format(band, index_type))
                    calc_cmd.extend(["-{0}".format(var), band_path])

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
                run_command(["gdalbuildvrt", "-separate", vrt_file] + normalized_paths)
                processed_tif = vrt_file

            logger.info("Converting to Cloud Optimized GeoTIFF: %s", final_output_file)

            if render_mode == "web_rgb" and len(normalized_paths) >= 3:
                scale_ranges: List[Tuple[float, float]] = []
                for p in normalized_paths[:3]:
                    band_min, band_max = get_band_stats(p, 1)
                    scale_ranges.append(safe_scale_range(band_min, band_max))
                build_final_cog(processed_tif, final_output_file, render_mode="web_rgb", scale_ranges=scale_ranges)
            elif index_type:
                build_final_cog(processed_tif, final_output_file, render_mode="analytic", is_float_index=True)
            else:
                build_final_cog(processed_tif, final_output_file, render_mode="analytic")

            self.send_status(job_id, "READY", "SENTINEL_COG", output_prefix=output_prefix)
            logger.info("Sentinel job %s completed successfully", job_id)

        except Exception as e:
            logger.exception("Sentinel job %s failed", job_id)
            self.send_status(job_id, "FAILED", "SENTINEL_COG", error_message=str(e), output_prefix=output_prefix)
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        if not output_prefix: return
        target = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))
        logger.info("Deleting data at %s", target)
        try:
            if os.path.isfile(target):
                os.remove(target)
            for suffix in (".aux.xml", ".ovr", ".msk"):
                sidecar = target + suffix
                if os.path.isfile(sidecar):
                    os.remove(sidecar)
        except Exception:
            logger.exception("Failed to delete %s", target)

    def normalize_band_name(self, channel: str) -> str:
        band = str(channel).strip().upper().replace(" ", "").replace("_", "")
        if not band.startswith("B"): return band
        suffix = band[1:]
        if suffix.isdigit():
            number = int(suffix)
            if number < 10: return "B0{0}".format(number)
            return "B{0}".format(number)
        return band

    def find_channel_path(self, extract_dir: str, channel: str) -> Optional[str]:
        canonical = self.normalize_band_name(channel)
        variants = [canonical]
        if canonical.startswith("B") and canonical[1:].isdigit():
            number = int(canonical[1:])
            if number < 10: variants.append("B{0}".format(number))
            else: variants.append("B0{0}".format(number))
        
        suffixes = ["", "_10m", "_20m", "_60m"]
        for variant in set(variants):
            for suffix in suffixes:
                pattern = os.path.join(extract_dir, "**/*_{0}{1}.jp2".format(variant, suffix))
                matches = glob.glob(pattern, recursive=True)
                if matches:
                    matches.sort()
                    return matches[0]
        return None
