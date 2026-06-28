import os
import shutil
import uuid
import time
import json
from datetime import datetime
from kafka import KafkaConsumer, KafkaProducer
from osgeo import gdal
from ..core.config import (
    KAFKA_BOOTSTRAP_SERVERS, KAFKA_TASKS_TOPIC, KAFKA_RESULTS_TOPIC, KAFKA_GROUP_ID,
    WORKSPACE_DIR, logger
)
from .s3_client import S3Client
from .plugin_manager import PluginManager

def ensure_vector_in_4326(local_path: str) -> str:
    from osgeo import ogr, osr, gdal
    
    try:
        # Check if it's a vector file by attempting to open it
        ds = ogr.Open(local_path)
        if ds is None:
            return local_path
        
        layer = ds.GetLayer()
        if layer is None:
            ds = None
            return local_path
            
        srs = layer.GetSpatialRef()
        if srs is None:
            ds = None
            return local_path
            
        # Target SRS: EPSG:4326
        target_srs = osr.SpatialReference()
        target_srs.ImportFromEPSG(4326)
        
        if srs.IsSame(target_srs):
            ds = None
            return local_path
            
        logger.info(f"Centrally reprojecting vector {local_path} from {srs.GetName()} to EPSG:4326")
        ds = None # Close to allow writing
        
        temp_reprojected_path = local_path + ".4326"
        ext = os.path.splitext(local_path)[1].lower()
        if ext == '.geojson':
            driver_name = 'GeoJSON'
        elif ext == '.gpkg':
            driver_name = 'GPKG'
        else:
            driver_name = 'ESRI Shapefile'
            
        # Reproject using high-level GDAL utility
        gdal.VectorTranslate(
            temp_reprojected_path,
            local_path,
            format=driver_name,
            dstSRS='EPSG:4326'
        )
        
        if os.path.exists(temp_reprojected_path):
            os.replace(temp_reprojected_path, local_path)
            logger.info(f"Successfully reprojected vector {local_path} to EPSG:4326")
            
    except Exception as e:
        logger.warning(f"Failed to check or reproject vector file {local_path}: {e}")
        
    return local_path

class Orchestrator:
    def __init__(self):
        self.s3_client = S3Client()
        self.plugin_manager = PluginManager()
        self.consumer = self._create_consumer()
        self.producer = self._create_producer()

    def _create_consumer(self):
        return KafkaConsumer(
            KAFKA_TASKS_TOPIC,
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
            group_id=KAFKA_GROUP_ID,
            auto_offset_reset="earliest",
            enable_auto_commit=False
        )

    def _create_producer(self):
        return KafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v).encode("utf-8")
        )

    def start(self):
        logger.info(f"GeoAnalysis Orchestrator started. Listening on {KAFKA_TASKS_TOPIC}")
        for message in self.consumer:
            task = message.value
            task_id = task.get("taskId", str(uuid.uuid4()))
            logger.info(f"Received task: {task_id}")
            
            try:
                result = self.process_task(task, task_id)
                self.producer.send(KAFKA_RESULTS_TOPIC, result)
            except Exception as e:
                logger.exception(f"Task {task_id} failed: {e}")
                error_result = {
                    "taskId": task_id,
                    "status": "FAILED",
                    "error": str(e),
                    "timestamp": datetime.now().isoformat()
                }
                self.producer.send(KAFKA_RESULTS_TOPIC, error_result)
            finally:
                self.consumer.commit()

    def process_task(self, task: dict, task_id: str):
        start_time = time.time()
        plugin_name = task.get("pluginName")
        inputs = task.get("inputs", {})
        params = task.get("parameters", {})
        
        plugin_cls = self.plugin_manager.get_plugin(plugin_name)
        if not plugin_cls:
            raise ValueError(f"Plugin not found: {plugin_name}")

        # Setup Workspace (tmpfs)
        workspace = os.path.join(WORKSPACE_DIR, f"task_{task_id}")
        os.makedirs(workspace, exist_ok=True)
        
        # Глобальная настройка песочницы GDAL для данной задачи
        gdal.SetConfigOption('CPL_TMPDIR', workspace)
        gdal.SetConfigOption('GDAL_CACHEMAX', '512') # Лимит кэша на задачу
        
        try:
            # Pull inputs from S3
            local_inputs = {}
            for key, s3_url in inputs.items():
                file_name = os.path.basename(s3_url)
                local_path = os.path.join(workspace, file_name)
                local_inputs[key] = self.s3_client.download_file(s3_url, local_path)

            # Execute plugin
            plugin = plugin_cls()
            logger.info(f"Executing plugin {plugin_name} for task {task_id}")
            local_outputs = plugin.run(local_inputs, params, workspace)

            # Push outputs to S3 (Staging bucket)
            s3_outputs = {}
            for key, local_path in local_outputs.items():
                local_path = ensure_vector_in_4326(local_path)
                file_name = os.path.basename(local_path)
                # Согласно стратегии: temp/{taskId}/...
                s3_key = f"temp/{task_id}/{file_name}"
                s3_outputs[key] = self.s3_client.upload_file(local_path, "gis-data", s3_key)

            execution_time = (time.time() - start_time) * 1000
            
            return {
                "taskId": task_id,
                "status": "COMPLETED",
                "outputs": s3_outputs,
                "metrics": {
                    "executionTimeMs": execution_time
                },
                "timestamp": datetime.now().isoformat()
            }

        finally:
            # Cleanup tmpfs workspace
            if os.path.exists(workspace):
                logger.info(f"Cleaning up workspace for task {task_id}")
                shutil.rmtree(workspace, ignore_errors=True)

def main():
    try:
        orchestrator = Orchestrator()
        orchestrator.start()
    except Exception as e:
        logger.exception(f"Critical error in main: {e}")

if __name__ == "__main__":
    main()
