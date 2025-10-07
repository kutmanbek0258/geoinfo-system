package kg.geoinfo.system.docservice.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.docservice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "geo.data.events", groupId = "document_service_geo_event_consumer")
    public void consumeGeoObjectEvents(GeoObjectEvent event) {
        log.info("Получено событие гео-объекта: {}", event);
        if (event.getEventType() == GeoObjectEvent.EventType.DELETED) {
            try {
                Map<String, Object> payload = event.getPayload();
                Object idObject = payload.get("id");
                if (idObject == null) {
                    log.error("Получено событие DELETED без 'id' в полезной нагрузке");
                    return;
                }
                UUID geoObjectId = UUID.fromString(idObject.toString());
                log.info("Обработка события DELETED для geoObjectId: {}", geoObjectId);
                documentService.deleteDocumentsByGeoObjectId(geoObjectId);
                log.info("Событие DELETED для geoObjectId: {} успешно обработано", geoObjectId);
            } catch (Exception e) {
                log.error("Ошибка при обработке события DELETED для гео-объекта: {}", e.getMessage(), e);
            }
        }
    }
}
