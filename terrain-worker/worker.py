import os
import json
import time
import logging
import subprocess
import shutil
import tempfile
from typing import Any, Dict, Optional, List

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable
from minio import Minio

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s"
)

logger = logging.getLogger("terrain-worker")

# -----------------------------------------------------------------------------
# Environment Variables
# -----------------------------------------------------------------------------

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "terrain.data.events")
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

# -----------------------------------------------------------------------------
# MinIO Client
# -----------------------------------------------------------------------------

minio_client = Minio(
    MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=MINIO_SECURE
)

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------

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
    logger.info("==================================")

def ensure_bucket_exists(bucket: str) -> None:
    try:
        if not minio_client.bucket_exists(bucket):
            minio_client.make_bucket(bucket)
            logger.info("Created bucket %s", bucket)
    except Exception:
        logger.exception("Failed to ensure bucket exists: %s", bucket)
        raise

def create_producer() -> KafkaProducer:
    while True:
        try:
            producer_instance = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                key_serializer=lambda v: v.encode("utf-8") if isinstance(v, str) else v,
                retries=10,
                retry_backoff_ms=3000,
                acks="all",
                linger_ms=5,
            )
            logger.info("Connected Kafka producer")
            return producer_instance
        except NoBrokersAvailable:
            logger.warning("Kafka producer unavailable, retrying in 5 seconds...")
            time.sleep(5)
        except Exception as e:
            logger.exception("Producer connection failed: %s", e)
            time.sleep(5)

def create_consumer() -> KafkaConsumer:
    while True:
        try:
            consumer_instance = KafkaConsumer(
                KAFKA_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                group_id=KAFKA_GROUP_ID,
                auto_offset_reset="earliest",
                enable_auto_commit=False,
                consumer_timeout_ms=1000,
                max_poll_records=1,
            )
            logger.info("Connected Kafka consumer")
            return consumer_instance
        except NoBrokersAvailable:
            logger.warning("Kafka consumer unavailable, retrying in 5 seconds...")
            time.sleep(5)
        except Exception as e:
            logger.exception("Consumer connection failed: %s", e)
            time.sleep(5)

def send_status(
    producer: KafkaProducer,
    job_id: str,
    event_type: str,
    error_message: Optional[str] = None,
    output_prefix: Optional[str] = None
) -> None:
    event: Dict[str, Any] = {
        "jobId": job_id,
        "eventType": event_type,
        "errorMessage": error_message,
        "outputPrefix": output_prefix,
        "timestamp": int(time.time() * 1000),
        "source": "terrain-worker"
    }

    try:
        future = producer.send(
            KAFKA_TOPIC,
            key=job_id,
            value=event
        )
        future.get(timeout=30)
        producer.flush()
        logger.info("Sent %s event for job %s", event_type, job_id)
    except Exception as e:
        logger.exception("Kafka send failed for job %s: %s", job_id, e)
        raise

def run_command(cmd: List[str], cwd: Optional[str] = None) -> None:
    logger.info("Running command: %s", " ".join(cmd))
    env = os.environ.copy()
    env["GDAL_CACHEMAX"] = GDAL_CACHEMAX

    result = subprocess.run(
        cmd,
        cwd=cwd,
        capture_output=True,
        text=True,
        env=env
    )

    if result.stdout:
        logger.info("stdout:\n%s", result.stdout.strip())

    if result.stderr:
        logger.info("stderr:\n%s", result.stderr.strip())

    if result.returncode != 0:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(cmd)}\n{result.stderr}"
        )

def save_tree(src_dir, dst_dir):
    """Очищает целевую папку и копирует туда новые данные."""
    if os.path.exists(dst_dir):
        logger.info("Cleaning up existing destination directory: %s", dst_dir)
        shutil.rmtree(dst_dir)

    # Создаем родительские папки, если их нет
    os.makedirs(os.path.dirname(dst_dir), exist_ok=True)
    shutil.copytree(src_dir, dst_dir)
    logger.info("Successfully copied tiles to %s", dst_dir)

def fix_layer_json(layer_json_path: str) -> None:
    """Исправляет layer.json для полной совместимости с CesiumJS."""
    if not os.path.exists(layer_json_path):
        raise RuntimeError("layer.json was not generated")

    with open(layer_json_path, "r", encoding="utf-8") as f:
        layer_json = json.load(f)

    # Исправляем опечатку в схеме, которую допускают некоторые версии CTB
    if "schema" in layer_json and "scheme" not in layer_json:
        layer_json["scheme"] = layer_json.pop("schema")

    # Устанавливаем стандарты для quantized-mesh
    layer_json["format"] = "quantized-mesh-1.0"
    if "version" not in layer_json:
        layer_json["version"] = "1.1.0"

    # Важно: шаблон путей к тайлам.
    # Убираем параметры запроса (?v=...), если Nginx настроен на простую раздачу.
    layer_json["tiles"] = ["{z}/{x}/{y}.terrain"]

    with open(layer_json_path, "w", encoding="utf-8") as f:
        json.dump(layer_json, f, ensure_ascii=False, indent=2)

def count_generated_tiles(output_dir: str) -> int:
    total = 0
    for root, _, files in os.walk(output_dir):
        for file_name in files:
            if file_name.endswith(".terrain"):
                total += 1
    return total

# -----------------------------------------------------------------------------
# Terrain Processing
# -----------------------------------------------------------------------------

def process_job(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    output_prefix = job_data["outputPrefix"]
    job_id = str(job_data["jobId"])

    # 1. Формируем финальный путь в хранилище (BASE_DIR = /data/terrain-store)
    final_output_path = os.path.join(BASE_DIR, output_prefix)

    source_bucket = job_data["sourceBucket"]
    source_key = job_data["sourceObjectKey"]

    logger.info(
        "Processing job %s: %s/%s -> Target: %s",
        job_id, source_bucket, source_key, final_output_path
    )

    # Создаем временную рабочую директорию в /tmp
    work_dir = tempfile.mkdtemp(prefix=f"terrain-{job_id}-")
    temp_tiles_dir = os.path.join(work_dir, "terrain_out")
    input_file = os.path.join(work_dir, "input.tif")
    normalized_file = os.path.join(work_dir, "normalized.tif")
    vrt_file = os.path.join(work_dir, "tiles.vrt")

    # Временная папка для генерации тайлов (внутри work_dir)
    temp_tiles_dir = os.path.join(work_dir, "terrain")

    try:
        send_status(producer, job_id, "PROCESSING", output_prefix=output_prefix)

        # --- Загрузка и подготовка (без изменений) ---
        logger.info("Downloading %s/%s", source_bucket, source_key)
        minio_client.fget_object(source_bucket, source_key, input_file)

        converted_file = os.path.join(work_dir, "converted.tif")
        run_command(["gdal_translate", input_file, converted_file, "-co", "TILED=YES", "-co", "COMPRESS=DEFLATE", "-co", "BIGTIFF=YES"])
        run_command(["gdalwarp", "-t_srs", GDAL_WARP_SRS, "-dstnodata", "-9999", "-multi", "-overwrite", converted_file, normalized_file])
        run_command(["gdal_translate", "-of", "VRT", normalized_file, vrt_file])

        os.makedirs(temp_tiles_dir, exist_ok=True)

        # 2. Генерация тайлов (используем -C для Compact Mesh)
        run_command(["ctb-tile", "-f", CTB_FORMAT, "-C", "-N", "-z", CTB_ZOOM, "-o", temp_tiles_dir, vrt_file])

        # 3. Генерация layer.json
        run_command(["ctb-tile", "-f", CTB_FORMAT, "-C", "-N", "-l", "-z", CTB_ZOOM, "-o", temp_tiles_dir, vrt_file])

        # 4. Модификация метаданных
        fix_layer_json(os.path.join(temp_tiles_dir, "layer.json"))

        tiles_count = count_generated_tiles(temp_tiles_dir)
        if tiles_count == 0:
            raise RuntimeError("No .terrain tiles were generated in temp directory")

        # 5. КЛЮЧЕВОЙ ШАГ: Перенос из временной папки в финальное хранилище /data/terrain-store
        logger.info("Moving result for job %s to permanent store", job_id)
        save_tree(temp_tiles_dir, final_output_path)

        send_status(producer, job_id, "READY", output_prefix=output_prefix)
        logger.info("Job %s completed. Tiles: %d", job_id, tiles_count)

    except Exception as e:
        logger.exception("Job %s failed", job_id)
        send_status(producer, job_id, "FAILED", error_message=str(e), output_prefix=output_prefix)
    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)
            logger.info("Cleaned up temporary work directory %s", work_dir)

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

if __name__ == "__main__":
    logger.info("Terrain worker starting...")
    log_config()

    producer = create_producer()
    consumer = create_consumer()

    logger.info("Terrain worker started, waiting for jobs...")

    while True:
        try:
            for message in consumer:
                event = message.value
                logger.info("Received event: %s", event)

                if event.get("eventType") == "QUEUED":
                    process_job(event, producer)

                consumer.commit()

        except KeyboardInterrupt:
            logger.info("Shutdown requested, exiting...")
            break

        except Exception as e:
            logger.exception("Consumer loop failed: %s", e)
            time.sleep(5)
            try:
                consumer.close(autocommit=False)
            except Exception:
                pass
            consumer = create_consumer()