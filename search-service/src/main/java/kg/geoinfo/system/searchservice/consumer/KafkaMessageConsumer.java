package kg.geoinfo.system.searchservice.consumer;

import kg.geoinfo.system.common.DocumentEvent;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.searchservice.service.IndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageConsumer {

    private final IndexingService indexingService;

    @KafkaListener(topics = "geo.data.events", groupId = "search-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeGeoDataEvent(GeoObjectEvent event) {
        log.info("Received event from geo.data.events: {}", event);
        indexingService.indexGeoObject(event);
    }

    @KafkaListener(topics = "doc.data.events", groupId = "search-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeDocDataEvent(DocumentEvent event) {
        log.info("Received event from doc.data.events: {}", event);
        indexingService.indexDocument(event);
    }

}