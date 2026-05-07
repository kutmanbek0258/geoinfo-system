import os
import json
import time
import logging
import subprocess
import shutil
import tempfile
import socket

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

KAFKA_BOOTSTRAP_SERVERS = os.getenv(
    "KAFKA_BOOTSTRAP_SERVERS",
    "kafka:9092"
)

KAFKA_TOPIC = os.getenv(
    "KAFKA_TOPIC",
    "terrain.data.events"
)

MINIO_ENDPOINT = os.getenv(
    "MINIO_ENDPOINT",
    "minio:9000"
)

MINIO_ACCESS_KEY = os.getenv(
    "MINIO_ROOT_USER",
    "admin"
)

MINIO_SECRET_KEY = os.getenv(
    "MINIO_ROOT_PASSWORD",
    "password"
)

# -----------------------------------------------------------------------------
# MinIO Client
# -----------------------------------------------------------------------------

minio_client = Minio(
    MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=False
)

def log_config():
    logger.info("===== TERRAIN WORKER CONFIG =====")
    logger.info(f"KAFKA_BOOTSTRAP_SERVERS = {KAFKA_BOOTSTRAP_SERVERS}")
    logger.info(f"KAFKA_TOPIC = {KAFKA_TOPIC}")
    logger.info(f"MINIO_ENDPOINT = {MINIO_ENDPOINT}")
    logger.info(f"MINIO_ACCESS_KEY = {MINIO_ACCESS_KEY}")
    logger.info(f"MINIO_SECRET_KEY = {'***' if MINIO_SECRET_KEY else None}")
    logger.info("==================================")

# -----------------------------------------------------------------------------
# Kafka
# -----------------------------------------------------------------------------

producer = None
consumer = None


def create_producer():
    while True:
        try:
            producer_instance = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                retries=10,
                retry_backoff_ms=3000,
                acks="all"
            )

            logger.info("Connected Kafka producer")

            return producer_instance

        except NoBrokersAvailable:
            logger.warning(
                "Kafka producer unavailable, retrying in 5 seconds..."
            )
            time.sleep(5)

        except Exception as e:
            logger.error(f"Producer connection failed: {e}")
            time.sleep(5)


def create_consumer():
    while True:
        try:
            consumer_instance = KafkaConsumer(
                KAFKA_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_deserializer=lambda m: json.loads(
                    m.decode("utf-8")
                ),
                group_id="terrain-worker-group",
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                consumer_timeout_ms=1000
            )

            logger.info("Connected Kafka consumer")

            return consumer_instance

        except NoBrokersAvailable:
            logger.warning(
                "Kafka consumer unavailable, retrying in 5 seconds..."
            )
            time.sleep(5)

        except Exception as e:
            logger.error(f"Consumer connection failed: {e}")
            time.sleep(5)


# -----------------------------------------------------------------------------
# Kafka Event Sender
# -----------------------------------------------------------------------------

def send_status(
    job_id,
    event_type,
    error_message=None,
    output_prefix=None
):
    global producer

    event = {
        "jobId": job_id,
        "eventType": event_type,
        "errorMessage": error_message,
        "outputPrefix": output_prefix
    }

    try:
        producer.send(
            KAFKA_TOPIC,
            key=job_id.encode("utf-8"),
            value=event
        )

        producer.flush()

        logger.info(
            f"Sent {event_type} event for job {job_id}"
        )

    except Exception as e:
        logger.error(f"Kafka send failed: {e}")

        producer = create_producer()

        producer.send(
            KAFKA_TOPIC,
            key=job_id.encode("utf-8"),
            value=event
        )

        producer.flush()


# -----------------------------------------------------------------------------
# Terrain Processing
# -----------------------------------------------------------------------------

def process_job(job_data):
    job_id = job_data["jobId"]
    source_bucket = job_data["sourceBucket"]
    source_key = job_data["sourceObjectKey"]
    output_bucket = job_data["outputBucket"]
    output_prefix = job_data["outputPrefix"]

    logger.info(
        f"Processing job {job_id}: "
        f"{source_bucket}/{source_key}"
    )

    work_dir = tempfile.mkdtemp(prefix=f"terrain-{job_id}-")

    input_file = os.path.join(work_dir, "input.tif")
    output_dir = os.path.join(work_dir, "terrain")

    try:
        # ---------------------------------------------------------------------
        # Send processing event
        # ---------------------------------------------------------------------

        send_status(job_id, "PROCESSING")

        # ---------------------------------------------------------------------
        # Download source raster
        # ---------------------------------------------------------------------

        logger.info(
            f"Downloading {source_bucket}/{source_key}"
        )

        minio_client.fget_object(
            source_bucket,
            source_key,
            input_file
        )

        logger.info(
            f"Downloaded to {input_file}"
        )

        # ---------------------------------------------------------------------
        # Create output directory
        # ---------------------------------------------------------------------

        os.makedirs(output_dir, exist_ok=True)

        # ---------------------------------------------------------------------
        # Run Cesium Terrain Builder
        # ---------------------------------------------------------------------

        cmd = [
            "ctb-tile",
            "-f", "Mesh",
            "-C",
            "-N",
            "-o", output_dir,
            input_file
        ]

        logger.info(
            f"Running command: {' '.join(cmd)}"
        )

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True
        )

        logger.info(result.stdout)

        if result.returncode != 0:
            raise Exception(
                f"ctb-tile failed: {result.stderr}"
            )

        # ---------------------------------------------------------------------
        # Upload generated terrain tiles
        # ---------------------------------------------------------------------

        logger.info(
            f"Uploading terrain to "
            f"{output_bucket}/{output_prefix}"
        )

        for root, dirs, files in os.walk(output_dir):
            for file in files:
                local_path = os.path.join(root, file)

                relative_path = os.path.relpath(
                    local_path,
                    output_dir
                )

                remote_path = (
                    f"{output_prefix}/{relative_path}"
                    .replace("\\", "/")
                )

                content_type = "application/octet-stream"

                if file.endswith(".json"):
                    content_type = "application/json"

                elif file.endswith(".terrain"):
                    content_type = (
                        "application/vnd.quantized-mesh"
                    )

                logger.info(
                    f"Uploading {remote_path}"
                )

                minio_client.fput_object(
                    output_bucket,
                    remote_path,
                    local_path,
                    content_type=content_type
                )

        logger.info(
            f"Job {job_id} completed successfully"
        )

        send_status(
            job_id,
            "READY",
            output_prefix=output_prefix
        )

    except Exception as e:
        logger.exception(
            f"Job {job_id} failed"
        )

        send_status(
            job_id,
            "FAILED",
            error_message=str(e)
        )

    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)

            logger.info(
                f"Cleaned up {work_dir}"
            )


# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

if __name__ == "__main__":
    logger.info("Terrain worker starting...")

#     logger.info(f"HOSTNAME = {socket.gethostname()}")
#     logger.info(f"RESOLVED kafka = {socket.gethostbyname_ex('kafka') if 'kafka' in KAFKA_BOOTSTRAP_SERVERS else 'N/A'}")

    log_config()

    producer = create_producer()
    consumer = create_consumer()

    logger.info("Terrain worker started, waiting for jobs...")

    while True:
        try:
            for message in consumer:
                event = message.value
                logger.info(f"Received event: {event}")

                if event.get("eventType") == "QUEUED":
                    process_job(event)

        except Exception as e:
            logger.exception(f"Consumer loop failed: {e}")
            time.sleep(5)
            consumer = create_consumer()