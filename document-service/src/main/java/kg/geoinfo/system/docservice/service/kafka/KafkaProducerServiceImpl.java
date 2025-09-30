
package kg.geoinfo.system.docservice.service.kafka;

import kg.geoinfo.system.common.DocumentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final String TOPIC = "doc.data.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendDocumentEvent(Map<String, Object> payload, DocumentEvent.EventType eventType) {
        try {
            DocumentEvent event = DocumentEvent.builder()
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            kafkaTemplate.send(TOPIC, event);
            log.info("Sent {} event to topic {}: {}", eventType, TOPIC, payload.get("id"));
        } catch (Exception e) {
            log.error("Error sending event to Kafka: {}", e.getMessage());
            // Consider adding error handling / dead-letter queue
        }
    }
}
