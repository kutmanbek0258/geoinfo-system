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

logger = get_logger("terrain-worker")

# -----------------------------------------------------------------------------
# Environment Variables
# -----------------------------------------------------------------------------
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "geoabstraction.terrain.events")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "terrain-worker-group")

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minio_access_key")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minio_secret_key")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

CTB_FORMAT = os.getenv("CTB_FORMAT", "Mesh")
CTB_ZOOM = os.getenv("CTB_ZOOM", "0-22")
GDAL_CACHEMAX = os.getenv("GDAL_CACHEMAX", "2048")  # MB
GDAL_WARP_SRS = os.getenv("GDAL_WARP_SRS", "EPSG:4326")

BASE_DIR = os.getenv("TERRAIN_STORE", "/data/terrain-store")

def log_config() -> None:
    logger.info("===== TERRAIN WORKER CONFIG =====")
    logger.info("KAFKA_BOOTSTRAP_SERVERS = %s", KAFKA_BOOTSTRAP_SERVERS)
    logger.info("KAFKA_TOPIC = %s", KAFKA_TOPIC)
    logger.info("KAFKA_GROUP_ID = %s", KAFKA_GROUP_ID)
    logger.info("MINIO_ENDPOINT = %s", MINIO_ENDPOINT)
    logger.info("MINIO_ACCESS_KEY = %s", MINIO_ACCESS_KEY)
    logger.info("MINIO_SECRET_KEY = %s", "***" if MINIO_SECRET_KEY else None)
    logger.info("MINIO_SECURE = %s", MINIO_SECURE)
    logger.info("CTB_FORMAT = %s", CTB_FORMAT)
    logger.info("CTB_ZOOM = %s", CTB_ZOOM)
    logger.info("GDAL_CACHEMAX = %s", GDAL_CACHEMAX)
    logger.info("GDAL_WARP_SRS = %s", GDAL_WARP_SRS)
    logger.info("TERRAIN_STORE = %s", BASE_DIR)
    logger.info("=========================================")
