import os
import shutil
import tempfile
from typing import Any, Dict, List, Optional, Tuple
from .base import BaseProcessor
from ..core.config import logger
from ..core.clients import minio_client
from ..core.gdal import run_command, get_band_stats, safe_scale_range, build_final_cog, get_wgs84_extent

class RawRasterProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        task_type = job_data.get("taskType", "RAW_GEOTIFF_OPTIMIZE")
        characteristics = job_data.get("characteristics", {})

        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]
        
        work_dir = tempfile.mkdtemp(prefix="raw-raster-{0}-".format(job_id))
        
        final_output_file = os.path.join(work_dir, "{0}.tif".format(output_prefix))
        input_file = os.path.join(work_dir, "input.tif")

        try:
            self.send_status(job_id, "PROCESSING", task_type, output_prefix=output_prefix)
            logger.info("Downloading raw GeoTIFF: %s/%s", source_bucket, source_key)
            minio_client.fget_object(source_bucket, source_key, input_file)

            logger.info("Validating downloaded GeoTIFF")
            run_command(["gdalinfo", input_file])

            logger.info("Converting raw GeoTIFF to Cloud Optimized GeoTIFF: %s", final_output_file)
            build_final_cog(input_file, final_output_file, render_mode="analytic")
            
            # Extract BBox
            bbox = get_wgs84_extent(final_output_file)

            if task_type == "TERRAIN_COG":
                cog_object_key = "{0}.tif".format(output_prefix)
                logger.info("Uploading Terrain COG to MinIO: %s/%s", source_bucket, cog_object_key)
                minio_client.fput_object(source_bucket, cog_object_key, final_output_file)
                self.send_status(job_id, "READY", task_type, output_prefix=output_prefix, cogObjectKey=cog_object_key, bbox=bbox)
            else:
                cog_object_key = "imagery-cog/{0}.tif".format(output_prefix)
                logger.info("Uploading optimized Raw COG to MinIO: %s/%s", source_bucket, cog_object_key)
                minio_client.fput_object(source_bucket, cog_object_key, final_output_file)
                self.send_status(job_id, "READY", task_type, output_prefix=output_prefix, cogObjectKey=cog_object_key, bbox=bbox)

            logger.info("Raw raster job %s completed successfully", job_id)

        except Exception as e:
            logger.exception("Raw raster job %s failed", job_id)
            self.send_status(job_id, "FAILED", task_type, error_message=str(e), output_prefix=output_prefix)
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        if not output_prefix: 
            return
            
        source_bucket = job_data.get("sourceBucket", "geo-abstraction-input")
        cog_key = "imagery-cog/{0}.tif".format(output_prefix)
        logger.info("Removing raw COG from MinIO: %s/%s", source_bucket, cog_key)
        
        try:
            minio_client.remove_object(source_bucket, cog_key)
            self.send_status(job_id, "DELETED", "RAW_GEOTIFF_OPTIMIZE", output_prefix=output_prefix)
        except Exception as e:
            logger.exception("Failed to cleanup job %s in MinIO", job_id)
            self.send_status(job_id, "FAILED", "RAW_GEOTIFF_OPTIMIZE", error_message=str(e), output_prefix=output_prefix)
