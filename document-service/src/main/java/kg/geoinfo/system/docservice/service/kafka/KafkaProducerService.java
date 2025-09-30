
package kg.geoinfo.system.docservice.service.kafka;

import kg.geoinfo.system.common.DocumentEvent;

import java.util.Map;

public interface KafkaProducerService {

    void sendDocumentEvent(Map<String, Object> payload, DocumentEvent.EventType eventType);

}
