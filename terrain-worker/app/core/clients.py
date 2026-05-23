import json
import time
from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import NoBrokersAvailable
from minio import Minio
from .config import (
    KAFKA_BOOTSTRAP_SERVERS, KAFKA_TOPIC, KAFKA_GROUP_ID,
    MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_SECURE,
    logger
)

minio_client = Minio(
    MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=MINIO_SECURE,
)

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
