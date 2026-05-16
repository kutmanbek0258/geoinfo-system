import os
import json
import time
import logging
import subprocess
import shutil
import tempfile
import zipfile
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

logger = logging.getLogger("geoabstraction-worker")

# -----------------------------------------------------------------------------
# Environment Variables
# -----------------------------------------------------------------------------

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
KAFKA_TOPIC = os.getenv("KAFKA_TOPIC", "geoabstraction.data.events")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "geoabstraction-worker-group")

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minio_access_key")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minio_secret_key")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

CTB_FORMAT = os.getenv("CTB_FORMAT", "Mesh")
CTB_ZOOM = os.getenv("CTB_ZOOM", "0-22")
GDAL_CACHEMAX = os.getenv("GDAL_CACHEMAX", "2048")  # MB
GDAL_WARP_SRS = os.getenv("GDAL_WARP_SRS", "EPSG:4326")

BASE_DIR = os.getenv("TERRAIN_STORE", "/data/terrain-store")
GDAL_STORE = os.getenv("GDAL_STORE", "/data/gdal-store")

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
    logger.info("===== GEOABSTRACTION WORKER CONFIG =====")
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
    logger.info("GDAL_STORE = %s", GDAL_STORE)
    logger.info("=========================================")

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
    error_message: Optional[str] = None,
    output_prefix: Optional[str] = None
) -> None:
    event: Dict[str, Any] = {
        "jobId": job_id,
        "eventType": event_type,
        "errorMessage": error_message,
        "outputPrefix": output_prefix,
        "timestamp": int(time.time() * 1000),
        "source": "geoabstraction-worker"
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

def get_elevation_band_index(file_path: str) -> Optional[int]:
    try:
        info_output = run_command(["gdalinfo", "-json", file_path])
        info = json.loads(info_output)
        bands = info.get("bands", [])
        if not bands: return None
        for i, band in enumerate(bands):
            color_interp = band.get("colorInterpretation", "Undefined")
            if color_interp not in ["Red", "Green", "Blue", "Alpha", "Palette"]:
                return i + 1
        return None
    except Exception:
        return None

def save_tree(src_dir, dst_dir):
    if os.path.exists(dst_dir):
        shutil.rmtree(dst_dir)
    os.makedirs(os.path.dirname(dst_dir), exist_ok=True)
    shutil.copytree(src_dir, dst_dir)

def fix_layer_json(layer_json_path: str) -> None:
    if not os.path.exists(layer_json_path):
        raise RuntimeError("layer.json was not generated")
    with open(layer_json_path, "r", encoding="utf-8") as f:
        layer_json = json.load(f)
    if "schema" in layer_json and "scheme" not in layer_json:
        layer_json["scheme"] = layer_json.pop("schema")
    layer_json["format"] = "quantized-mesh-1.0"
    layer_json["tiles"] = ["{z}/{x}/{y}.terrain"]
    with open(layer_json_path, "w", encoding="utf-8") as f:
        json.dump(layer_json, f, ensure_ascii=False, indent=2)

def count_generated_tiles(output_dir: str) -> int:
    total = 0
    for root, _, files in os.walk(output_dir):
        for f in files:
            if f.endswith(".terrain"): total += 1
    return total

# -----------------------------------------------------------------------------
# Dispatcher & Processors
# -----------------------------------------------------------------------------

def process_job_dispatcher(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    task_type = job_data.get("taskType", "TERRAIN_MESH")
    logger.info("Dispatching job %s with task type %s", job_data["jobId"], task_type)
    
    if task_type == "TERRAIN_MESH":
        process_terrain_mesh(job_data, producer)
    elif task_type == "SENTINEL_COG":
        process_sentinel_cog(job_data, producer)
    else:
        logger.error("Unknown task type: %s", task_type)
        send_status(producer, job_data["jobId"], "FAILED", error_message=f"Unknown task type: {task_type}")

def process_sentinel_cog(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    job_id = str(job_data["jobId"])
    output_prefix = job_data["outputPrefix"]
    characteristics = job_data.get("characteristics", {})
    channels = characteristics.get("channels", [])
    
    if not channels:
        logger.error("No channels specified for Sentinel-2 COG job %s", job_id)
        send_status(producer, job_id, "FAILED", error_message="No spectral channels specified")
        return

    source_bucket = job_data["sourceBucket"]
    source_key = job_data["sourceObjectKey"]
    final_output_file = os.path.join(GDAL_STORE, f"{output_prefix}.tif")
    
    work_dir = tempfile.mkdtemp(prefix=f"sentinel-{job_id}-")
    zip_file = os.path.join(work_dir, "source.zip")
    extract_dir = os.path.join(work_dir, "extracted")
    
    try:
        send_status(producer, job_id, "PROCESSING", output_prefix=output_prefix)
        logger.info("Downloading Sentinel archive: %s/%s", source_bucket, source_key)
        minio_client.fget_object(source_bucket, source_key, zip_file)
        
        with zipfile.ZipFile(zip_file, 'r') as zip_ref:
            zip_ref.extractall(extract_dir)
            
        channel_paths = []
        for channel in channels:
            found = False
            for root, _, files in os.walk(extract_dir):
                for f in files:
                    if f.endswith(f"_{channel}.jp2") or f.endswith(f"_{channel}_10m.jp2") or f.endswith(f"_{channel}_20m.jp2"):
                        channel_paths.append(os.path.join(root, f))
                        found = True
                        break
                if found: break
            if not found:
                logger.warning("Channel %s not found in SAFE archive", channel)

        if not channel_paths:
            raise RuntimeError("None of the specified channels were found in the archive")

        logger.info("Found %d channels for merging: %s", len(channel_paths), channels)
        vrt_file = os.path.join(work_dir, "merged.vrt")
        run_command(["gdalbuildvrt", "-separate", vrt_file] + channel_paths)
        
        logger.info("Converting to Cloud Optimized GeoTIFF: %s", final_output_file)
        os.makedirs(os.path.dirname(final_output_file), exist_ok=True)
        
        run_command([
            "gdal_translate", vrt_file, final_output_file,
            "-of", "COG",
            "-co", "COMPRESS=DEFLATE",
            "-co", "PREDICTOR=2",
            "-co", "NUM_THREADS=ALL_CPUS",
            "-co", "BIGTIFF=YES"
        ])
        
        send_status(producer, job_id, "READY", output_prefix=output_prefix)
        logger.info("Sentinel COG job %s completed successfully", job_id)

    except Exception as e:
        logger.exception("Sentinel job %s failed", job_id)
        send_status(producer, job_id, "FAILED", error_message=str(e), output_prefix=output_prefix)
    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)

def process_terrain_mesh(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    output_prefix = job_data["outputPrefix"]
    job_id = str(job_data["jobId"])
    final_output_path = os.path.join(BASE_DIR, output_prefix)
    source_bucket = job_data["sourceBucket"]
    source_key = job_data["sourceObjectKey"]

    work_dir = tempfile.mkdtemp(prefix=f"terrain-{job_id}-")
    input_file = os.path.join(work_dir, "input.tif")
    normalized_file = os.path.join(work_dir, "normalized.tif")
    vrt_file = os.path.join(work_dir, "tiles.vrt")
    temp_tiles_dir = os.path.join(work_dir, "terrain")

    try:
        send_status(producer, job_id, "PROCESSING", output_prefix=output_prefix)
        minio_client.fget_object(source_bucket, source_key, input_file)
        elevation_band_idx = get_elevation_band_index(input_file)
        if elevation_band_idx is None:
            raise RuntimeError("The provided file does not contain an elevation band (DEM)")

        extracted_dem = os.path.join(work_dir, "extracted_dem.tif")
        run_command(["gdal_translate", "-b", str(elevation_band_idx), input_file, extracted_dem, "-co", "COMPRESS=DEFLATE"])
        converted_file = os.path.join(work_dir, "converted.tif")
        run_command(["gdal_translate", extracted_dem, converted_file, "-co", "TILED=YES", "-co", "COMPRESS=DEFLATE", "-co", "BIGTIFF=YES"])
        run_command(["gdalwarp", "-t_srs", GDAL_WARP_SRS, "-dstnodata", "-9999", "-multi", "-overwrite", converted_file, normalized_file])
        run_command(["gdal_translate", "-of", "VRT", normalized_file, vrt_file])

        os.makedirs(temp_tiles_dir, exist_ok=True)
        run_command(["ctb-tile", "-f", CTB_FORMAT, "-C", "-N", "-z", CTB_ZOOM, "-o", temp_tiles_dir, vrt_file])
        run_command(["ctb-tile", "-f", CTB_FORMAT, "-C", "-N", "-l", "-z", CTB_ZOOM, "-o", temp_tiles_dir, vrt_file])
        fix_layer_json(os.path.join(temp_tiles_dir, "layer.json"))

        if count_generated_tiles(temp_tiles_dir) == 0:
            raise RuntimeError("No .terrain tiles were generated")

        save_tree(temp_tiles_dir, final_output_path)
        send_status(producer, job_id, "READY", output_prefix=output_prefix)
    except Exception as e:
        logger.exception("Terrain job %s failed", job_id)
        send_status(producer, job_id, "FAILED", error_message=str(e), output_prefix=output_prefix)
    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)

def delete_data(job_data: Dict[str, Any]) -> None:
    output_prefix = job_data.get("outputPrefix")
    task_type = job_data.get("taskType", "TERRAIN_MESH")
    if not output_prefix: return

    if task_type == "TERRAIN_MESH":
        target = os.path.join(BASE_DIR, output_prefix)
    else:
        target = os.path.join(GDAL_STORE, f"{output_prefix}.tif")
        
    logger.info("Deleting data at %s", target)
    try:
        if os.path.isdir(target): shutil.rmtree(target)
        elif os.path.isfile(target): os.remove(target)
    except Exception:
        logger.exception("Failed to delete %s", target)

if __name__ == "__main__":
    logger.info("GeoAbstraction worker starting...")
    log_config()
    producer = create_producer()
    consumer = create_consumer()
    logger.info("GeoAbstraction worker started, waiting for jobs...")
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
