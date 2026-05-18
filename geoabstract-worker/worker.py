
import os
import json
import time
import logging
import subprocess
import shutil
import tempfile
import zipfile
import glob
import re
from typing import Any, Dict, Optional, List, Tuple

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable
from minio import Minio

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s :: %(message)s"
)
logger = logging.getLogger("geoabstract-worker")

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

INDEX_FORMULAS = {
    "NDVI": "(B8 - B4) / (B8 + B4)",
    "EVI": "2.5 * (B8 - B4) / (B8 + 6 * B4 - 7.5 * B2 + 1)",
    "SAVI": "1.5 * (B8 - B4) / (B8 + B4 + 0.5)",
    "GNDVI": "(B8 - B3) / (B8 + B3)",
    "NDMI": "(B8 - B11) / (B8 + B11)",
    "NBR": "(B8 - B12) / (B8 + B12)",
    "NDSI": "(B3 - B11) / (B3 + B11)",
    "NDBI": "(B11 - B8) / (B11 + B8)",
    "NDWI": "(B3 - B8) / (B3 + B8)",
}

INDEX_BANDS = {
    "NDVI": ["B08", "B04"],
    "EVI": ["B08", "B04", "B02"],
    "SAVI": ["B08", "B04"],
    "GNDVI": ["B08", "B03"],
    "NDMI": ["B08", "B11"],
    "NBR": ["B08", "B12"],
    "NDSI": ["B03", "B11"],
    "NDBI": ["B11", "B08"],
    "NDWI": ["B03", "B08"],
}

minio_client = Minio(
    MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=MINIO_SECURE,
)

def log_config() -> None:
    logger.info("===== GEOABSTRACT WORKER CONFIG =====")
    logger.info("KAFKA_BOOTSTRAP_SERVERS = %s", KAFKA_BOOTSTRAP_SERVERS)
    logger.info("KAFKA_TOPIC = %s", KAFKA_TOPIC)
    logger.info("KAFKA_GROUP_ID = %s", KAFKA_GROUP_ID)
    logger.info("MINIO_ENDPOINT = %s", MINIO_ENDPOINT)
    logger.info("GDAL_CACHEMAX = %s", GDAL_CACHEMAX)
    logger.info("GDAL_STORE = %s", GDAL_STORE)
    logger.info("DEFAULT_WEB_RGB_MAX = %s", DEFAULT_WEB_RGB_MAX)
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
            logger.warning("Kafka producer unavailable, retrying in 5 seconds.")
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
            logger.warning("Kafka consumer unavailable, retrying in 5 seconds.")
            time.sleep(5)
        except Exception as e:
            logger.exception("Consumer connection failed: %s", e)
            time.sleep(5)

def send_status(
    producer: KafkaProducer,
    job_id: str,
    event_type: str,
    task_type: str,
    error_message: Optional[str] = None,
    output_prefix: Optional[str] = None,
) -> None:
    event: Dict[str, Any] = {
        "jobId": job_id,
        "eventType": event_type,
        "taskType": task_type,
        "errorMessage": error_message,
        "outputPrefix": output_prefix,
        "timestamp": int(time.time() * 1000),
        "source": "geoabstract-worker",
    }

    try:
        future = producer.send(KAFKA_TOPIC, key=job_id, value=event)
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
        env=env,
    )

    if result.stdout:
        logger.info("stdout:\n%s", result.stdout.strip())
    if result.stderr:
        logger.info("stderr:\n%s", result.stderr.strip())

    if result.returncode != 0:
        raise RuntimeError(
            "Command failed ({0}): {1}\n{2}".format(
                result.returncode,
                " ".join(cmd),
                result.stderr,
            )
        )

    return result.stdout

def normalize_band_name(channel: str) -> str:
    if channel is None:
        return channel

    band = str(channel).strip().upper().replace(" ", "").replace("_", "")
    if not band.startswith("B"):
        return band

    suffix = band[1:]
    if suffix.isdigit():
        number = int(suffix)
        if number < 10:
            return "B0{0}".format(number)
        return "B{0}".format(number)

    return band

def band_variants(channel: str) -> List[str]:
    canonical = normalize_band_name(channel)
    variants = [canonical]
    if canonical.startswith("B") and canonical[1:].isdigit():
        number = int(canonical[1:])
        if number < 10:
            variants.append("B{0}".format(number))
        else:
            variants.append("B0{0}".format(number))
    unique = []
    for item in variants:
        if item not in unique:
            unique.append(item)
    return unique

def find_channel_path(extract_dir: str, channel: str) -> Optional[str]:
    variants = band_variants(channel)
    suffixes = ["", "_10m", "_20m", "_60m"]

    for variant in variants:
        for suffix in suffixes:
            pattern = os.path.join(extract_dir, "**/*_{0}{1}.jp2".format(variant, suffix))
            matches = glob.glob(pattern, recursive=True)
            if matches:
                matches.sort()
                logger.info("Found channel %s -> %s", channel, matches[0])
                return matches[0]

    all_jp2 = glob.glob(os.path.join(extract_dir, "**/*.jp2"), recursive=True)
    for variant in variants:
        for path in all_jp2:
            base = os.path.basename(path).upper()
            if ("_{0}_".format(variant) in base) or base.endswith("_{0}.JP2".format(variant)):
                logger.info("Found channel %s -> %s", channel, path)
                return path

    logger.warning("Channel %s not found in archive", channel)
    return None

def build_gdal_calc_formula(index_type: str) -> str:
    formula = INDEX_FORMULAS[index_type]
    expected_bands = INDEX_BANDS[index_type]

    replacements: List[Tuple[str, str]] = []
    for idx, band in enumerate(expected_bands):
        var = chr(ord("A") + idx)
        canonical = normalize_band_name(band)
        replacements.append((canonical, var))
        replacements.append((canonical.replace("0", ""), var))

    for token, var in sorted(replacements, key=lambda kv: len(kv[0]), reverse=True):
        formula = formula.replace(token, var)

    return formula

def get_band_stats(path: str, band_index: int = 1) -> Tuple[Optional[float], Optional[float]]:
    try:
        out = run_command(["gdalinfo", "-stats", "-json", path])
        info = json.loads(out)
        bands = info.get("bands", [])
        if 1 <= band_index <= len(bands):
            band = bands[band_index - 1]
            min_val = band.get("minimum")
            if min_val is None:
                min_val = band.get("computedMin")
            if min_val is None:
                min_val = band.get("approximateMinimum")

            max_val = band.get("maximum")
            if max_val is None:
                max_val = band.get("computedMax")
            if max_val is None:
                max_val = band.get("approximateMaximum")

            if min_val is not None and max_val is not None:
                return float(min_val), float(max_val)
    except Exception:
        pass

    try:
        out = run_command(["gdalinfo", "-stats", path])
        current_band = 0
        seen_target = False
        for line in out.splitlines():
            if line.startswith("Band "):
                current_band += 1
                seen_target = (current_band == band_index)
                continue
            if not seen_target:
                continue
            m = re.search(r"Minimum=([-\d\.eE]+),\s*Maximum=([-\d\.eE]+)", line)
            if m:
                return float(m.group(1)), float(m.group(2))
    except Exception:
        pass

    return None, None

def safe_scale_range(min_val: Optional[float], max_val: Optional[float], default_max: float = DEFAULT_WEB_RGB_MAX) -> Tuple[float, float]:
    if max_val is None and min_val is None:
        return 0.0, default_max
    if min_val is None:
        min_val = 0.0
    if max_val is None:
        max_val = default_max
    min_val = max(0.0, float(min_val))
    max_val = float(max_val)
    if max_val <= min_val:
        max_val = min_val + 1.0
    return min_val, max_val

def build_final_cog(
    source_path: str,
    output_path: str,
    render_mode: str,
    scale_ranges: Optional[List[Tuple[float, float]]] = None,
    is_float_index: bool = False,
) -> None:
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    cmd = ["gdal_translate", source_path, output_path, "-of", "COG"]

    if render_mode == "web_rgb":
        cmd.extend(["-ot", "Byte"])
        if scale_ranges:
            for idx, (src_min, src_max) in enumerate(scale_ranges[:3], start=1):
                cmd.extend([
                    "-scale_{0}".format(idx),
                    str(src_min),
                    str(src_max),
                    "0",
                    "255",
                ])
        else:
            for idx in range(1, 4):
                cmd.extend([
                    "-scale_{0}".format(idx),
                    "0",
                    str(DEFAULT_WEB_RGB_MAX),
                    "0",
                    "255",
                ])
    elif is_float_index:
        cmd.extend(["-ot", "Float32"])

    cmd.extend([
        "-co", "COMPRESS=DEFLATE",
        "-co", "NUM_THREADS=ALL_CPUS",
        "-co", "BIGTIFF=YES",
    ])

    run_command(cmd)
    try:
        run_command(["gdal_edit.py", "-stats", output_path])
    except Exception as e:
        logger.warning("Could not write stats for %s: %s", output_path, e)

def determine_render_mode(characteristics: Dict[str, Any], index_type: Optional[str], channels: List[str]) -> str:
    explicit = str(characteristics.get("outputMode", characteristics.get("renderMode", ""))).strip().lower()
    if explicit in ("web_rgb", "rgb", "preview", "display"):
        return "web_rgb"
    if explicit in ("analytic", "analysis", "raw"):
        return "analytic"
    if index_type:
        return "analytic"
    if len(channels) == 3:
        return "web_rgb"
    return "analytic"

def process_sentinel_cog(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    job_id = str(job_data.get("jobId"))
    output_prefix = job_data.get("outputPrefix")
    characteristics = job_data.get("characteristics", {})

    if not job_id or not output_prefix:
        raise RuntimeError("jobId or outputPrefix is missing")

    index_type = characteristics.get("indexType")
    channels = characteristics.get("channels", [])

    if index_type and index_type in INDEX_BANDS:
        logger.info("Job %s is an index calculation: %s", job_id, index_type)
        channels = INDEX_BANDS[index_type]

    if not channels:
        logger.error("No channels or index specified for Sentinel-2 job %s", job_id)
        send_status(
            producer,
            job_id,
            "FAILED",
            "SENTINEL_COG",
            error_message="No spectral channels specified",
            output_prefix=output_prefix,
        )
        return

    render_mode = determine_render_mode(characteristics, index_type, channels)

    source_bucket = job_data["sourceBucket"]
    source_key = job_data["sourceObjectKey"]
    final_output_file = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))

    work_dir = tempfile.mkdtemp(prefix="sentinel-{0}-".format(job_id))
    zip_file = os.path.join(work_dir, "source.zip")
    extract_dir = os.path.join(work_dir, "extracted")

    try:
        send_status(producer, job_id, "PROCESSING", "SENTINEL_COG", output_prefix=output_prefix)
        logger.info("Downloading Sentinel archive: %s/%s", source_bucket, source_key)
        minio_client.fget_object(source_bucket, source_key, zip_file)

        with zipfile.ZipFile(zip_file, "r") as zip_ref:
            zip_ref.extractall(extract_dir)

        normalized_paths: List[str] = []
        normalized_band_names: List[str] = []

        for channel in channels:
            src_path = find_channel_path(extract_dir, channel)
            if not src_path:
                raise RuntimeError("Channel {0} not found in archive".format(channel))

            canonical = normalize_band_name(channel)
            norm_path = os.path.join(work_dir, "{0}_10m.tif".format(canonical))

            logger.info("Normalizing channel %s to 10m -> %s", channel, norm_path)
            run_command([
                "gdalwarp",
                "-overwrite",
                "-tr", "10", "10",
                "-tap",
                "-r", "bilinear",
                "-multi",
                "-wo", "NUM_THREADS=ALL_CPUS",
                "-srcnodata", "0",
                "-dstnodata", "0",
                src_path,
                norm_path,
            ])

            normalized_paths.append(norm_path)
            normalized_band_names.append(canonical)

        processed_tif = os.path.join(work_dir, "processed.tif")

        if index_type:
            if index_type not in INDEX_BANDS:
                raise RuntimeError("Unsupported index type: {0}".format(index_type))

            expected = [normalize_band_name(b) for b in INDEX_BANDS[index_type]]
            path_by_band: Dict[str, str] = {}
            for band_name, band_path in zip(normalized_band_names, normalized_paths):
                path_by_band[band_name] = band_path
                path_by_band[band_name.replace("0", "")] = band_path

            calc_cmd = ["gdal_calc.py"]
            for idx, band in enumerate(expected):
                var = chr(ord("A") + idx)
                band_path = path_by_band.get(band) or path_by_band.get(band.replace("0", ""))
                if not band_path:
                    raise RuntimeError("Required band {0} missing for {1}".format(band, index_type))
                calc_cmd.extend(["-{0}".format(var), band_path])

            formula = build_gdal_calc_formula(index_type)
            calc_cmd.extend([
                "--calc", formula,
                "--outfile", processed_tif,
                "--NoDataValue", "-9999",
                "--type", "Float32",
                "--overwrite",
            ])
            run_command(calc_cmd)
        else:
            vrt_file = os.path.join(work_dir, "merged.vrt")
            run_command(["gdalbuildvrt", "-separate", vrt_file] + normalized_paths)
            processed_tif = vrt_file

        logger.info("Converting to Cloud Optimized GeoTIFF: %s", final_output_file)

        if render_mode == "web_rgb" and len(normalized_paths) >= 3:
            scale_ranges: List[Tuple[float, float]] = []
            for p in normalized_paths[:3]:
                band_min, band_max = get_band_stats(p, 1)
                scale_ranges.append(safe_scale_range(band_min, band_max))
            build_final_cog(
                processed_tif,
                final_output_file,
                render_mode="web_rgb",
                scale_ranges=scale_ranges,
                is_float_index=False,
            )
        elif index_type:
            build_final_cog(
                processed_tif,
                final_output_file,
                render_mode="analytic",
                scale_ranges=None,
                is_float_index=True,
            )
        else:
            build_final_cog(
                processed_tif,
                final_output_file,
                render_mode="analytic",
                scale_ranges=None,
                is_float_index=False,
            )

        send_status(producer, job_id, "READY", "SENTINEL_COG", output_prefix=output_prefix)
        logger.info("Sentinel job %s completed successfully", job_id)

    except Exception as e:
        logger.exception("Sentinel job %s failed", job_id)
        send_status(
            producer,
            job_id,
            "FAILED",
            "SENTINEL_COG",
            error_message=str(e),
            output_prefix=output_prefix,
        )
    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)

def delete_data(job_data: Dict[str, Any]) -> None:
    output_prefix = job_data.get("outputPrefix")
    task_type = job_data.get("taskType", "SENTINEL_COG")
    if not output_prefix or task_type != "SENTINEL_COG":
        return

    target = os.path.join(GDAL_STORE, "{0}.tif".format(output_prefix))
    logger.info("Deleting data at %s", target)
    try:
        if os.path.isfile(target):
            os.remove(target)
        for suffix in (".aux.xml", ".ovr", ".msk"):
            sidecar = target + suffix
            if os.path.isfile(sidecar):
                os.remove(sidecar)
    except Exception:
        logger.exception("Failed to delete %s", target)

def process_job_dispatcher(job_data: Dict[str, Any], producer: KafkaProducer) -> None:
    task_type = job_data.get("taskType", "SENTINEL_COG")
    job_id = job_data.get("jobId", "unknown")
    logger.info("Dispatching job %s with task type %s", job_id, task_type)

    if task_type == "SENTINEL_COG":
        process_sentinel_cog(job_data, producer)
    else:
        logger.error("Unsupported task type for geoabstract-worker: %s", task_type)

def main() -> None:
    logger.info("GeoAbstract worker starting.")
    log_config()

    producer = create_producer()
    consumer = create_consumer()

    logger.info("GeoAbstract worker started, waiting for jobs.")

    while True:
        try:
            for message in consumer:
                event = message.value or {}
                event_type = event.get("eventType")

                if event_type == "QUEUED":
                    process_job_dispatcher(event, producer)
                elif event_type == "DELETED":
                    delete_data(event)

                consumer.commit()

        except KeyboardInterrupt:
            break
        except Exception as e:
            logger.exception("Failed processing message: %s", e)
            time.sleep(5)
            try:
                consumer.close(autocommit=False)
            except Exception:
                pass
            consumer = create_consumer()

if __name__ == "__main__":
    main()
