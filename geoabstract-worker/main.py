import time
import os
import shutil
import tempfile
from app.core.config import logger, KAFKA_TOPIC
from app.core.clients import create_producer, create_consumer
from app.processors.factory import get_processor

def cleanup_on_startup():
    """Removes orphaned temporary directories from /tmp on startup."""
    temp_dir = tempfile.gettempdir()
    prefixes = ["sentinel-", "landsat8-", "raw-raster-"]
    logger.info("Starting startup cleanup in %s", temp_dir)
    
    try:
        for item in os.listdir(temp_dir):
            path = os.path.join(temp_dir, item)
            if os.path.isdir(path) and any(item.startswith(p) for p in prefixes):
                logger.info("Removing orphaned directory: %s", path)
                shutil.rmtree(path, ignore_errors=True)
    except Exception as e:
        logger.warning("Failed to perform startup cleanup: %s", e)

def main():
    logger.info("GeoAbstract worker starting (Modular Version).")
    cleanup_on_startup()
    
    producer = create_producer()
    consumer = create_consumer()

    logger.info("GeoAbstract worker started, waiting for jobs.")

    while True:
        try:
            for message in consumer:
                event = message.value or {}
                event_type = event.get("eventType")
                task_type = event.get("taskType")
                job_id = event.get("jobId", "unknown")

                processor = get_processor(task_type, producer)
                
                if not processor:
                    if event_type == "QUEUED":
                        logger.error("Unsupported task type: %s for job %s", task_type, job_id)
                    consumer.commit()
                    continue

                if event_type == "QUEUED":
                    logger.info("Processing job %s (%s)", job_id, task_type)
                    processor.process(event)
                elif event_type == "DELETED" and event.get("source") != "geoabstract-worker":
                    logger.info("Deleting data for job %s", job_id)
                    processor.cleanup(event)

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
