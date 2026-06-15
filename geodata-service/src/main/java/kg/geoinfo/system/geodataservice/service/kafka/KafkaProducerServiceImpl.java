package kg.geoinfo.system.geodataservice.service.kafka;

import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.common.GeoVectorExportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final String GEO_DATA_TOPIC = "geo.data.events";
    private static final String VECTOR_EXPORT_RESPONSE_TOPIC = "geo.vector.export.results";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendGeoObjectEvent(Map<String, Object> payload, GeoObjectEvent.EventType eventType) {
        try {
            kg.geoinfo.system.common.GeoObjectEvent event = kg.geoinfo.system.common.GeoObjectEvent.builder()
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            
            Object id = payload.get("id");
            String key = id != null ? id.toString() : null;
            
            kafkaTemplate.send(GEO_DATA_TOPIC, key, event);
            log.info("Sent {} event to topic {}: {}", eventType, GEO_DATA_TOPIC, key);
        } catch (Exception e) {
            log.error("Error sending geo object event to Kafka: {}", e.getMessage());
        }
    }

    @Override
    public void sendGeoVectorExportResponse(GeoVectorExportResponse response) {
        try {
            String key = response.getTaskId().toString();
            kafkaTemplate.send(VECTOR_EXPORT_RESPONSE_TOPIC, key, response);
            log.info("Sent vector export response to topic {}: {}", VECTOR_EXPORT_RESPONSE_TOPIC, key);
        } catch (Exception e) {
            log.error("Error sending vector export response to Kafka: {}", e.getMessage());
        }
    }
}
