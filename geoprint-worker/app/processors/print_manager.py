import os
import shutil
import tempfile
import time
from kafka import KafkaProducer
import json
import gc
from ..core.config import logger, KAFKA_PRODUCE_TOPIC, MINIO_BUCKET_REPORTS, MINIO_BUCKET_RASTER
from ..core.clients import minio_client
from .map_renderer import render_map
from .layout_builder import build_pdf_report

class PrintManager:
    def __init__(self, producer: KafkaProducer):
        self.producer = producer

    def process_task(self, event_data: dict):
        task_id = event_data.get("taskId")
        spec = event_data.get("spec")
        
        if not task_id:
            logger.error("No taskId provided in event data")
            return
            
        if not spec:
            logger.error("No print specification (spec) provided in event data for task %s", task_id)
            self.send_result(task_id, "FAILED", error_message="No print specification provided")
            return

        logger.info("Processing print task: %s", task_id)
        
        work_dir = tempfile.mkdtemp(prefix=f"print-{task_id}-")
        map_png = os.path.join(work_dir, "map.png")
        output_pdf = os.path.join(work_dir, f"{task_id}.pdf")
        
        try:
            # Check and fetch any S3-based vector layers
            for layer in spec.get("layers", []):
                if layer.get("type") == "VECTOR" and not layer.get("features"):
                    url_str = str(layer.get("url", ""))
                    if url_str.startswith("s3://") or url_str.startswith("temp/print/"):
                        s3_key = url_str.replace(f"s3://{MINIO_BUCKET_RASTER}/", "").replace("s3://", "")
                        logger.info("Downloading vector layer features from S3: %s", s3_key)
                        local_geojson = os.path.join(work_dir, f"vector_{id(layer)}.geojson")
                        
                        minio_client.fget_object(MINIO_BUCKET_RASTER, s3_key, local_geojson)
                        with open(local_geojson, "r", encoding="utf-8") as f:
                            geojson_data = json.load(f)
                            
                        layer["features"] = geojson_data.get("features", [])
                        layer["url"] = None

            # 1. Render map to PNG
            render_map(spec, map_png)
            
            # 2. Build PDF report
            build_pdf_report(map_png, output_pdf, spec)
            
            # 3. Upload PDF to MinIO
            s3_key = f"reports/{task_id}.pdf"
            logger.info("Uploading PDF to MinIO: %s/%s", MINIO_BUCKET_REPORTS, s3_key)
            if not minio_client.bucket_exists(MINIO_BUCKET_REPORTS):
                minio_client.make_bucket(MINIO_BUCKET_REPORTS)
            minio_client.fput_object(MINIO_BUCKET_REPORTS, s3_key, output_pdf, "application/pdf")
            
            # 4. Send success result
            self.send_result(task_id, "COMPLETED", s3_key=s3_key)
            
        except Exception as e:
            logger.exception("Failed to process print task %s", task_id)
            self.send_result(task_id, "FAILED", error_message=str(e))
        finally:
            # Cleanup temp folder and force garbage collection
            try:
                shutil.rmtree(work_dir)
            except Exception as e:
                logger.warn("Failed to cleanup work directory %s: %s", work_dir, str(e))
            gc.collect()

    def send_result(self, task_id: str, status: str, s3_key: str = None, error_message: str = None):
        result = {
            "taskId": task_id,
            "status": status,
            "s3Key": s3_key,
            "errorMessage": error_message
        }
        
        try:
            future = self.producer.send(
                KAFKA_PRODUCE_TOPIC,
                key=task_id.encode('utf-8'),
                value=json.dumps(result).encode('utf-8')
            )
            future.get(timeout=30)
            self.producer.flush()
            logger.info("Sent result event to Kafka: %s (Status: %s)", task_id, status)
        except Exception as e:
            logger.exception("Failed to send Kafka print result for %s: %s", task_id, str(e))
