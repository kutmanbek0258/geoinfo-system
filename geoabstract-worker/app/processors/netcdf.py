import os
import shutil
import tempfile
from typing import Any, Dict
from osgeo import gdal
from .base import BaseProcessor
from ..core.config import logger
from ..core.clients import minio_client
from ..core.gdal import run_command, build_final_cog, get_wgs84_extent

class NetcdfProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        task_type = job_data.get("taskType", "NETCDF_COG")
        characteristics = job_data.get("characteristics", {})
        variable_name = characteristics.get("netcdfVariable")

        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")
        if not variable_name:
            raise RuntimeError("netcdfVariable parameter is missing for NetCDF import")

        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]
        
        work_dir = tempfile.mkdtemp(prefix="netcdf-{0}-".format(job_id))
        input_file = os.path.join(work_dir, "input.nc")
        final_output_file = os.path.join(work_dir, "{0}.tif".format(output_prefix))

        try:
            self.send_status(job_id, "PROCESSING", task_type, output_prefix=output_prefix)
            logger.info("Downloading NetCDF file: %s/%s", source_bucket, source_key)
            minio_client.fget_object(source_bucket, source_key, input_file)

            # Reconstruct subdataset key
            subdataset_path = 'NETCDF:"{0}":{1}'.format(input_file, variable_name)
            logger.info("Opening NetCDF subdataset: %s", subdataset_path)

            # Check if dataset has projection
            ds = gdal.Open(subdataset_path)
            has_projection = False
            if ds:
                proj = ds.GetProjection()
                if proj and len(proj.strip()) > 0:
                    has_projection = True
                ds = None

            if not has_projection:
                logger.info("Subdataset has no projection. Force assigning EPSG:4326.")
                georef_file = os.path.join(work_dir, "georef.tif")
                run_command(["gdal_translate", "-a_srs", "EPSG:4326", subdataset_path, georef_file])
                subdataset_path = georef_file

            logger.info("Converting NetCDF subdataset to Cloud Optimized GeoTIFF: %s", final_output_file)
            build_final_cog(subdataset_path, final_output_file, render_mode="analytic")
            
            # Extract BBox
            bbox = get_wgs84_extent(final_output_file)

            cog_object_key = "imagery-cog/{0}.tif".format(output_prefix)
            logger.info("Uploading NetCDF COG to MinIO: %s/%s", source_bucket, cog_object_key)
            minio_client.fput_object(source_bucket, cog_object_key, final_output_file)
            
            self.send_status(job_id, "READY", task_type, output_prefix=output_prefix, cogObjectKey=cog_object_key, bbox=bbox)
            logger.info("NetCDF job %s completed successfully", job_id)

        except Exception as e:
            logger.exception("NetCDF job %s failed", job_id)
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
        logger.info("Removing NetCDF COG from MinIO: %s/%s", source_bucket, cog_key)
        
        try:
            minio_client.remove_object(source_bucket, cog_key)
            self.send_status(job_id, "DELETED", "NETCDF_COG", output_prefix=output_prefix)
        except Exception as e:
            logger.exception("Failed to cleanup job %s in MinIO", job_id)
            self.send_status(job_id, "FAILED", "NETCDF_COG", error_message=str(e), output_prefix=output_prefix)
