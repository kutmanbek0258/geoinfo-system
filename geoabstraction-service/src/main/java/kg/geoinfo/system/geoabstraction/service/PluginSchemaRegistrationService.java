package kg.geoinfo.system.geoabstraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geoabstraction.models.PluginSchema;
import kg.geoinfo.system.geoabstraction.repository.PluginSchemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginSchemaRegistrationService {

    private static final String SCHEMA_REQUEST_TOPIC = "geoanalysis.schema.request";
    private static final String SCHEMA_RESPONSE_TOPIC = "geoanalysis.schema.response";

    private final PluginSchemaRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initSchemaRequest() {
        log.info("Application started. Resetting registered schemas and requesting updates from worker...");
        try {
            repository.deleteAll();
            log.info("Cleared old plugin schemas from DB.");

            Map<String, Object> request = Map.of(
                    "action", "GET_SCHEMAS",
                    "timestamp", LocalDateTime.now().toString()
            );
            kafkaTemplate.send(SCHEMA_REQUEST_TOPIC, "request", request);
            log.info("Sent schema request to topic {}", SCHEMA_REQUEST_TOPIC);
        } catch (Exception e) {
            log.error("Failed to initialize schema registration: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = SCHEMA_RESPONSE_TOPIC,
            containerFactory = "stringKafkaListenerContainerFactory",
            groupId = "${spring.kafka.consumer.group-id:geoabstraction-service-group}-schema"
    )
    @Transactional
    public void listenSchemaResponse(String message) {
        log.info("Received schema response from worker.");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String pluginName = (String) event.get("pluginName");
            String title = (String) event.get("title");
            String icon = (String) event.get("icon");
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) event.get("schema");

            if (pluginName == null || schema == null) {
                log.warn("Invalid schema registration payload received: {}", event);
                return;
            }

            PluginSchema entity = new PluginSchema();
            entity.setPluginName(pluginName);
            entity.setTitle(title != null ? title : pluginName);
            entity.setIcon(icon != null ? icon : "mdi-puzzle-outline");
            entity.setSchema(schema);
            entity.setRegisteredAt(LocalDateTime.now());

            repository.save(entity);
            log.info("Registered plugin schema in DB: {}", pluginName);
        } catch (Exception e) {
            log.error("Failed to parse and register plugin schema: {}", e.getMessage(), e);
        }
    }
}
