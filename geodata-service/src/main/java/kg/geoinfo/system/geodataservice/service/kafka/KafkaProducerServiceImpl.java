
package kg.geoinfo.system.geodataservice.service.kafka;

import kg.geoinfo.system.common.GeoObjectEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final String TOPIC = "geo.data.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendGeoObjectEvent(Map<String, Object> payload, GeoObjectEvent.EventType eventType) {
        try {
            GeoObjectEvent event = GeoObjectEvent.builder()
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            log.info(event.toString());
            kafkaTemplate.send(TOPIC, event);
            log.info("Sent {} event to topic {}: {}", eventType, TOPIC, payload.get("id"));
        } catch (Exception e) {
            log.error("Error sending event to Kafka: {}", e.getMessage());
            // Consider adding error handling / dead-letter queue
        }
    }
}
