import time
from abc import ABC, abstractmethod
from typing import Any, Dict, Optional
from kafka import KafkaProducer
from ..core.config import logger, KAFKA_TOPIC

class BaseProcessor(ABC):
    def __init__(self, producer: KafkaProducer):
        self.producer = producer

    @abstractmethod
    def process(self, job_data: Dict[str, Any]) -> None:
        pass

    @abstractmethod
    def cleanup(self, job_data: Dict[str, Any]) -> None:
        pass

    def send_status(
        self,
        job_id: str,
        event_type: str,
        task_type: str,
        error_message: Optional[str] = None,
        output_prefix: Optional[str] = None,
        **extra_fields: Any,
    ) -> None:
        event: Dict[str, Any] = {
            "jobId": job_id,
            "eventType": event_type,
            "taskType": task_type,
            "errorMessage": error_message,
            "outputPrefix": output_prefix,
            "timestamp": int(time.time() * 1000),
            "source": "terrain-worker",
        }
        event.update(extra_fields)

        try:
            future = self.producer.send(KAFKA_TOPIC, key=job_id, value=event)
            future.get(timeout=30)
            self.producer.flush()
            logger.info("Sent %s event for job %s", event_type, job_id)
        except Exception as e:
            logger.exception("Kafka send failed for job %s: %s", job_id, e)
            raise
