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

logger = get_logger("geoabstract-worker")

# -----------------------------------------------------------------------------
# Environment Variables
# -----------------------------------------------------------------------------
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "geoabstraction.raster.events")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "geoabstract-worker-group")

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minio_access_key")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minio_secret_key")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

GDAL_CACHEMAX = os.getenv("GDAL_CACHEMAX", "2048")
GDAL_STORE = os.getenv("GDAL_STORE", "/data/gdal-store")
DEFAULT_WEB_RGB_MAX = float(os.getenv("DEFAULT_WEB_RGB_MAX", "8"))
