package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final String TERRAIN_TOPIC = "geoabstraction.terrain.events";
    private static final String RASTER_TOPIC = "geoabstraction.raster.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendGeoAbstractJobEvent(GeoAbstractJobEvent event) {
        try {
            String topic = TERRAIN_TOPIC;
            if ("SENTINEL_COG".equals(event.getTaskType()) || "LANDSAT_COG".equals(event.getTaskType())) {
                topic = RASTER_TOPIC;
            }
            
            kafkaTemplate.send(topic, event.getJobId().toString(), event);
            log.info("Sent {} event to topic {}: {}", event.getEventType(), topic, event.getJobId());
        } catch (Exception e) {
            log.error("Error sending event to Kafka: {}", e.getMessage());
        }
    }
}
