import os
import tempfile
from typing import Any, Dict
from osgeo import gdal, ogr
from kafka import KafkaProducer
from ..core.config import logger
from ..core.clients import minio_client
from .base import BaseProcessor

class ShapefileProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = job_data.get("jobId")
        task_type = job_data.get("taskType", "SHAPEFILE_TO_GEOJSON")
        source_bucket = job_data.get("sourceBucket")
        source_object_key = job_data.get("sourceObjectKey")
        output_bucket = job_data.get("outputBucket")
        output_prefix = job_data.get("outputPrefix", f"vector-geojson/{job_id}")

        logger.info("Starting Shapefile -> GeoJSON processing for job %s", job_id)
        self.send_status(job_id, "PROCESSING", task_type, output_prefix=output_prefix)

        with tempfile.TemporaryDirectory() as tmpdir:
            local_zip_path = os.path.join(tmpdir, "input.zip")
            local_geojson_path = os.path.join(tmpdir, "output.json")

            try:
                # 1. Download ZIP file from MinIO
                minio_client.fget_object(source_bucket, source_object_key, local_zip_path)


                # 2. Open via GDAL /vsizip/
                vsi_path = f"/vsizip/{local_zip_path}"
                ds = gdal.OpenEx(vsi_path, gdal.OF_VECTOR)
                if ds is None:
                    raise RuntimeError(f"Failed to open Shapefile ZIP via GDAL: {source_object_key}")

                # Count total features
                feature_count = 0
                for i in range(ds.GetLayerCount()):
                    layer = ds.GetLayerByIndex(i)
                    feature_count += layer.GetFeatureCount()

                ds = None # close dataset before VectorTranslate

                # 3. Convert Shapefile to GeoJSON in EPSG:4326 WGS-84
                translate_options = gdal.VectorTranslateOptions(
                    format="GeoJSON",
                    dstSRS="EPSG:4326",
                    reproject=True,
                    layerCreationOptions=["COORDINATE_PRECISION=7"]
                )

                # Set UTF-8 encoding config for Shapefile attributes
                gdal.SetConfigOption("SHAPE_ENCODING", "UTF-8")
                res_ds = gdal.VectorTranslate(local_geojson_path, vsi_path, options=translate_options)
                if res_ds is None:
                    raise RuntimeError("GDAL VectorTranslate failed to produce GeoJSON output")
                res_ds = None

                # 4. Upload GeoJSON to MinIO
                geojson_object_key = f"{output_prefix}.json" if not output_prefix.endswith(".json") else output_prefix
                minio_client.fput_object(output_bucket, geojson_object_key, local_geojson_path, content_type="application/json")
                logger.info("Successfully uploaded GeoJSON to S3: %s", geojson_object_key)


                # 5. Send READY event to Kafka
                self.send_status(
                    job_id,
                    "READY",
                    task_type,
                    output_prefix=output_prefix,
                    cogObjectKey=geojson_object_key,
                    crs="EPSG:4326",
                    featureCount=feature_count
                )

            except Exception as e:
                logger.exception("Failed Shapefile processing for job %s: %s", job_id, e)
                self.send_status(
                    job_id,
                    "FAILED",
                    task_type,
                    error_message=str(e),
                    output_prefix=output_prefix
                )

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        job_id = job_data.get("jobId")
        task_type = job_data.get("taskType", "SHAPEFILE_TO_GEOJSON")
        output_prefix = job_data.get("outputPrefix", f"vector-geojson/{job_id}")
        self.send_status(job_id, "DELETED", task_type, output_prefix=output_prefix)
