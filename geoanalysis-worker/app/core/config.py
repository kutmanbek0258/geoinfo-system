import os
import logging

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s"
)

def get_logger(name):
    return logging.getLogger(name)

logger = get_logger("geoanalysis-worker")

# -----------------------------------------------------------------------------
# Environment Variables
# -----------------------------------------------------------------------------
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TASKS_TOPIC = os.getenv("KAFKA_TASKS_TOPIC", "geoabstraction.tasks")
KAFKA_RESULTS_TOPIC = os.getenv("KAFKA_RESULTS_TOPIC", "geoabstraction.results")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "geoanalysis-worker-group")
KAFKA_SCHEMA_REQUEST_TOPIC = os.getenv("KAFKA_SCHEMA_REQUEST_TOPIC", "geoanalysis.schema.request")
KAFKA_SCHEMA_RESPONSE_TOPIC = os.getenv("KAFKA_SCHEMA_RESPONSE_TOPIC", "geoanalysis.schema.response")

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minio_access_key")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minio_secret_key")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

WORKSPACE_DIR = os.getenv("WORKSPACE_DIR", "/app/workspace")
TASK_TIMEOUT_SEC = int(os.getenv("TASK_TIMEOUT_SEC", "600")) # 10 minutes
