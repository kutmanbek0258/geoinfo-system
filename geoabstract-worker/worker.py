import os
import json
import time
import logging
import subprocess
import shutil
import tempfile
import zipfile
import glob
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

logger = logging.getLogger("geoabstract-worker")

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

GDAL_CACHEMAX = os.getenv("GDAL_CACHEMAX", "2048")  # MB
GDAL_STORE = os.getenv("GDAL_STORE", "/data/gdal-store")

# -----------------------------------------------------------------------------
# Spectral Constants
# -----------------------------------------------------------------------------

INDEX_FORMULAS = {
    "NDVI": "(B8 - B4) / (B8 + B4)",
    "EVI": "2.5 * (B8 - B4) / (B8 + 6 * B4 - 7.5 * B2 + 1)",
    "SAVI": "1.5 * (B8 - B4) / (B8 + B4 + 0.5)",
    "GNDVI": "(B8 - B3) / (B8 + B3)",
    "NDMI": "(B8 - B11) / (B8 + B11)",
    "NBR": "(B8 - B12) / (B8 + B12)",
    "NDSI": "(B3 - B11) / (B3 + B11)",
    "NDBI": "(B11 - B8) / (B11 + B8)",
    "NDWI": "(B3 - B8) / (B3 + B8)"
}

# Mapping index to required bands
INDEX_BANDS = {
    "NDVI": ["B08", "B04"],
    "EVI": ["B08", "B04", "B02"],
    "SAVI": ["B08", "B04"],
    "GNDVI": ["B08", "B03"],
    "NDMI": ["B08", "B11"],
    "NBR": ["B08", "B12"],
    "NDSI": ["B03", "B11"],
    "NDBI": ["B11", "B08"],
    "NDWI": ["B03", "B08"]
}

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
    logger.info("===== GEOABSTRACT WORKER CONFIG =====")
    logger.info("KAFKA_BOOTSTRAP_SERVERS = %s", KAFKA_BOOTSTRAP_SERVERS)
    logger.info("KAFKA_TOPIC = %s", KAFKA_TOPIC)
    logger.info("KAFKA_GROUP_ID = %s", KAFKA_GROUP_ID)
    logger.info("MINIO_ENDPOINT = %s", MINIO_ENDPOINT)
    logger.info("GDAL_CACHEMAX = %s", GDAL_CACHEMAX)
    logger.info("GDAL_STORE = %s", GDAL_STORE)
    logger.info("=========================================")

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
                session_timeout_ms=600000,
                request_timeout_ms=601000,
                connections_max_idle_ms=660000,
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
    taskType: str,
    error_message: Optional[str] = None,
    output_prefix: Optional[str] = None
) -> None:
    event: Dict[str, Any] = {
        "jobId": job_id,
        "eventType": event_type,
        "taskType": taskType,
        "errorMessage": error_message,
        "outputPrefix": output_prefix,
        "timestamp": int(time.time() * 1000),
        "source": "geoabstract-worker"
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

def run_command(cmd: List[str], cwd: Optional[str] = None) -> str:
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
    
    return result.stdout

# -----------------------------------------------------------------------------
# Sentinel Processors
# -----------------------------------------------------------------------------

def find_channel_path(extract_dir: str, channel: str) -> Optional[str]:
    # Sentinel-2 files can have different resolution suffixes
    patterns = [
        f"**/*_{channel}.jp2",
        f"**/*_{channel}_10m.jp2",
        f"**/*_{channel}_20m.jp2",
        f"**/*_{channel}_60m.jp2"
    ]
    for pattern in patterns:
        matches = glob.glob(os.path.join(extract_dir, pattern), recursive=True)
        if matches:
            return matches[0]
    return None

def process_sentinel_cog(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    job_id = str(job_data["jobId"])
    output_prefix = job_data["outputPrefix"]
    characteristics = job_data.get("characteristics", {})
    
    # Check if we have an index calculation or a standard channel list
    index_type = characteristics.get("indexType")
    channels = characteristics.get("channels", [])
    
    if index_type and index_type in INDEX_BANDS:
        logger.info("Job %s is an index calculation: %s", job_id, index_type)
        channels = INDEX_BANDS[index_type]
    
    if not channels:
        logger.error("No channels or index specified for Sentinel-2 job %s", job_id)
        send_status(producer, job_id, "FAILED", "SENTINEL_COG", error_message="No spectral channels specified")
        return

    source_bucket = job_data["sourceBucket"]
    source_key = job_data["sourceObjectKey"]
    final_output_file = os.path.join(GDAL_STORE, f"{output_prefix}.tif")
    
    work_dir = tempfile.mkdtemp(prefix=f"sentinel-{job_id}-")
    zip_file = os.path.join(work_dir, "source.zip")
    extract_dir = os.path.join(work_dir, "extracted")
    
    try:
        send_status(producer, job_id, "PROCESSING", "SENTINEL_COG", output_prefix=output_prefix)
        logger.info("Downloading Sentinel archive: %s/%s", source_bucket, source_key)
        minio_client.fget_object(source_bucket, source_key, zip_file)
        
        with zipfile.ZipFile(zip_file, 'r') as zip_ref:
            zip_ref.extractall(extract_dir)
            
        # 1. Collect and Normalize Channels
        # We need all channels to be at the same resolution (10m) for math/merging
        normalized_paths = []
        channel_to_var = {} # For gdal_calc mapping
        
        for idx, channel in enumerate(channels):
            src_path = find_channel_path(extract_dir, channel)
            if not src_path:
                raise RuntimeError(f"Channel {channel} not found in archive")
            
            norm_path = os.path.join(work_dir, f"{channel}_10m.tif")
            logger.info("Normalizing channel %s to 10m", channel)
            # Warp to 10m resolution using bilinear resampling
            run_command([
                "gdalwarp",
                "-tr", "10", "10",
                "-r", "bilinear",
                "-overwrite",
                src_path,
                norm_path
            ])
            normalized_paths.append(norm_path)
            # Map B02 -> A, B03 -> B, etc. for gdal_calc
            var_name = chr(ord('A') + idx)
            channel_to_var[channel.replace("0", "")] = var_name # B08 -> B8
            channel_to_var[channel] = var_name # B08 -> B08

        # 2. Process: Index Calculation or RGB Merge
        processed_tif = os.path.join(work_dir, "processed.tif")
        
        if index_type:
            # Index Calculation using gdal_calc
            formula = INDEX_FORMULAS[index_type]
            # Replace band names in formula with gdal_calc variables
            # Order: B8, B4, B2, B3, B11, B12
            calc_cmd = ["gdal_calc.py"]
            for band_name, var in channel_to_var.items():
                if band_name in formula:
                    calc_cmd.extend([f"-{var}", os.path.join(work_dir, f"{band_name}_10m.tif") if "_" not in band_name else os.path.join(work_dir, f"{band_name}_10m.tif")])
            
            # Re-fetch normalized paths to ensure we use the right ones
            calc_cmd = ["gdal_calc.py"]
            for idx, channel in enumerate(channels):
                var = chr(ord('A') + idx)
                calc_cmd.extend([f"-{var}", normalized_paths[idx]])
            
            # Build formula with variables
            # Note: This is a bit tricky, let's simplify by passing variables based on order in INDEX_BANDS
            f_bands = INDEX_BANDS[index_type]
            f_formula = INDEX_FORMULAS[index_type]
            for idx, b in enumerate(f_bands):
                var = chr(ord('A') + idx)
                # Replace both B08 and B8
                f_formula = f_formula.replace(b, var).replace(b.replace("0", ""), var)
            
            calc_cmd.extend(["--calc", f_formula, "--outfile", processed_tif, "--NoDataValue", "-9999"])
            run_command(calc_cmd)
        else:
            # Standard Merge to Multi-band VRT then TIF
            vrt_file = os.path.join(work_dir, "merged.vrt")
            run_command(["gdalbuildvrt", "-separate", vrt_file] + normalized_paths)
            processed_tif = vrt_file

        # 3. Final Conversion to COG
        logger.info("Converting to Cloud Optimized GeoTIFF: %s", final_output_file)
        os.makedirs(os.path.dirname(final_output_file), exist_ok=True)
        
        run_command([
            "gdal_translate",
            processed_tif,
            final_output_file,
            "-of", "COG",
            "-co", "COMPRESS=DEFLATE",
            "-co", "PREDICTOR=2",
            "-co", "NUM_THREADS=ALL_CPUS",
            "-co", "BIGTIFF=YES"
        ])
        
        send_status(producer, job_id, "READY", "SENTINEL_COG", output_prefix=output_prefix)
        logger.info("Sentinel job %s completed successfully", job_id)

    except Exception as e:
        logger.exception("Sentinel job %s failed", job_id)
        send_status(producer, job_id, "FAILED", "SENTINEL_COG", error_message=str(e), output_prefix=output_prefix)
    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)

def delete_data(job_data: Dict[str, Any]) -> None:
    output_prefix = job_data.get("outputPrefix")
    task_type = job_data.get("taskType", "SENTINEL_COG")
    if not output_prefix or task_type != "SENTINEL_COG": return

    target = os.path.join(GDAL_STORE, f"{output_prefix}.tif")
        
    logger.info("Deleting data at %s", target)
    try:
        if os.path.isfile(target): os.remove(target)
    except Exception:
        logger.exception("Failed to delete %s", target)

# -----------------------------------------------------------------------------
# Dispatcher
# -----------------------------------------------------------------------------

def process_job_dispatcher(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    task_type = job_data.get("taskType", "SENTINEL_COG")
    logger.info("Dispatching job %s with task type %s", job_data["jobId"], task_type)
    
    if task_type == "SENTINEL_COG":
        process_sentinel_cog(job_data, producer)
    else:
        logger.error("Unsupported task type for geoabstract-worker: %s", task_type)

if __name__ == "__main__":
    logger.info("GeoAbstract worker starting...")
    log_config()
    producer = create_producer()
    consumer = create_consumer()
    logger.info("GeoAbstract worker started, waiting for jobs...")
    while True:
        try:
            for message in consumer:
                event = message.value
                event_type = event.get("eventType")
                if event_type == "QUEUED":
                    process_job_dispatcher(event, producer)
                elif event_type == "DELETED":
                    delete_data(event)
                consumer.commit()
        except KeyboardInterrupt: break
        except Exception:
            time.sleep(5)
            try: consumer.close(autocommit=False)
            except Exception: pass
            consumer = create_consumer()
