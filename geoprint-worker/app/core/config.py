import os
import logging

# Configure logger
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s"
)
logger = logging.getLogger("geoprint-worker")

# Kafka Settings
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092").split(",")
KAFKA_CONSUME_TOPIC = os.getenv("KAFKA_CONSUME_TOPIC", "geo.print.tasks")
KAFKA_PRODUCE_TOPIC = os.getenv("KAFKA_PRODUCE_TOPIC", "geo.print.results")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "geoprint-worker-group")

# MinIO Settings
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minio_access_key")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minio_secret_key")
MINIO_SECURE = os.getenv("MINIO_SECURE", "False").lower() in ("true", "1", "yes")
MINIO_BUCKET_REPORTS = os.getenv("MINIO_BUCKET_REPORTS", "geo-print")
MINIO_BUCKET_RASTER = os.getenv("MINIO_BUCKET_RASTER", "geo-abstraction-input")
