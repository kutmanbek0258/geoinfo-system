import os
import shutil
import uuid
import time
import json
from datetime import datetime
from kafka import KafkaConsumer, KafkaProducer
from .config import (
    KAFKA_BOOTSTRAP_SERVERS, KAFKA_TASKS_TOPIC, KAFKA_RESULTS_TOPIC, KAFKA_GROUP_ID,
    WORKSPACE_DIR, TASK_TIMEOUT_SEC, logger
)
from .s3_manager import S3Manager
from .plugin_loader import PluginLoader

class Orchestrator:
    def __init__(self):
        self.s3_manager = S3Manager()
        self.plugin_loader = PluginLoader()
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
        logger.info(f"Orchestrator started. Listening on {KAFKA_TASKS_TOPIC}")
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
        
        plugin_cls = self.plugin_loader.get_plugin(plugin_name)
        if not plugin_cls:
            raise ValueError(f"Plugin not found: {plugin_name}")

        # Setup Workspace
        workspace = os.path.join(WORKSPACE_DIR, f"task_{task_id}")
        os.makedirs(workspace, exist_ok=True)
        
        try:
            # Pull inputs
            local_inputs = {}
            for key, s3_url in inputs.items():
                file_name = os.path.basename(s3_url)
                local_path = os.path.join(workspace, file_name)
                local_inputs[key] = self.s3_manager.download_file(s3_url, local_path)

            # Execute plugin
            plugin = plugin_cls()
            logger.info(f"Executing plugin {plugin_name} for task {task_id}")
            local_outputs = plugin.run(local_inputs, params, workspace)

            # Push outputs
            s3_outputs = {}
            for key, local_path in local_outputs.items():
                file_name = os.path.basename(local_path)
                s3_key = f"processed/{plugin_name}/{task_id}/{file_name}"
                s3_outputs[key] = self.s3_manager.upload_file(local_path, "gis-processed-data", s3_key)

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
            # Cleanup
            if os.path.exists(workspace):
                logger.info(f"Cleaning up workspace for task {task_id}")
                shutil.rmtree(workspace, ignore_errors=True)
