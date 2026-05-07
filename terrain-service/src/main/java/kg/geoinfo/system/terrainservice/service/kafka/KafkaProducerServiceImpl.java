package kg.geoinfo.system.terrainservice.service.kafka;

import kg.geoinfo.system.common.TerrainJobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final String TOPIC = "terrain.data.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendTerrainJobEvent(TerrainJobEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getJobId().toString(), event);
            log.info("Sent {} event to topic {}: {}", event.getEventType(), TOPIC, event.getJobId());
        } catch (Exception e) {
            log.error("Error sending event to Kafka: {}", e.getMessage());
        }
    }
}
