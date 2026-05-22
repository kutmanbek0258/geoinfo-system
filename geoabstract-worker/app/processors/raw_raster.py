import os
import shutil
import tempfile
from typing import Any, Dict, List, Optional, Tuple
from .base import BaseProcessor
from ..core.config import logger, GDAL_STORE
from ..core.clients import minio_client
from ..core.gdal import run_command, get_band_stats, safe_scale_range, build_final_cog

class RawRasterProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        characteristics = job_data.get("characteristics", {})

        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]
        final_output_file = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))

        work_dir = tempfile.mkdtemp(prefix="raw-raster-{0}-".format(job_id))
        input_file = os.path.join(work_dir, "input.tif")

        try:
            self.send_status(job_id, "PROCESSING", "RAW_GEOTIFF_OPTIMIZE", output_prefix=output_prefix)
            logger.info("Downloading raw GeoTIFF: %s/%s", source_bucket, source_key)
            minio_client.fget_object(source_bucket, source_key, input_file)

            logger.info("Validating downloaded GeoTIFF")
            run_command(["gdalinfo", input_file])

            logger.info("Converting raw GeoTIFF to Cloud Optimized GeoTIFF: %s", final_output_file)
            
            # For raw GeoTIFF, we just optimize it to COG. 
            # We assume it's already in a good projection or let COG driver handle it.
            # Usually we want to ensure it's Byte if it's for simple viewing, 
            # but user said they want original values for SLD.
            
            build_final_cog(input_file, final_output_file, render_mode="analytic")

            self.send_status(job_id, "READY", "RAW_GEOTIFF_OPTIMIZE", output_prefix=output_prefix)
            logger.info("Raw raster job %s completed successfully", job_id)

        except Exception as e:
            logger.exception("Raw raster job %s failed", job_id)
            self.send_status(job_id, "FAILED", "RAW_GEOTIFF_OPTIMIZE", error_message=str(e), output_prefix=output_prefix)
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        if not output_prefix: return
        target = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))
        logger.info("Deleting raw raster data at %s", target)
        try:
            if os.path.isfile(target):
                os.remove(target)
            for suffix in (".aux.xml", ".ovr", ".msk"):
                sidecar = target + suffix
                if os.path.isfile(sidecar):
                    os.remove(sidecar)
        except Exception:
            logger.exception("Failed to delete %s", target)
