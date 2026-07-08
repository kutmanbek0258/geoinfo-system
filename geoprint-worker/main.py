import sys
import json
import time
from kafka import KafkaConsumer, KafkaProducer
from app.core.config import logger, KAFKA_BOOTSTRAP_SERVERS, KAFKA_CONSUME_TOPIC, KAFKA_GROUP_ID
from app.processors.print_manager import PrintManager

def main():
    logger.info("Starting geoprint-worker...")
    
    # Connect Kafka consumer
    consumer = None
    retries = 10
    while retries > 0:
        try:
            consumer = KafkaConsumer(
                KAFKA_CONSUME_TOPIC,
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                group_id=KAFKA_GROUP_ID,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                enable_auto_commit=True
            )
            logger.info("Connected to Kafka consumer on topic %s", KAFKA_CONSUME_TOPIC)
            break
        except Exception as e:
            logger.warn("Failed to connect to Kafka, retrying in 5 seconds... Error: %s", str(e))
            retries -= 1
            time.sleep(5)
            
    if not consumer:
        logger.error("Could not establish Kafka consumer connection. Exiting.")
        sys.exit(1)

    # Connect Kafka producer
    producer = None
    retries = 10
    while retries > 0:
        try:
            producer = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS
            )
            logger.info("Connected to Kafka producer")
            break
        except Exception as e:
            logger.warn("Failed to connect to Kafka producer, retrying in 5 seconds... Error: %s", str(e))
            retries -= 1
            time.sleep(5)
            
    if not producer:
        logger.error("Could not establish Kafka producer connection. Exiting.")
        sys.exit(1)

    manager = PrintManager(producer)

    logger.info("Listening for print task events...")
    for message in consumer:
        try:
            event_data = message.value
            logger.info("Received print task event: %s", event_data)
            manager.process_task(event_data)
        except Exception as e:
            logger.exception("Error in consumer message loop: %s", str(e))

if __name__ == "__main__":
    main()
