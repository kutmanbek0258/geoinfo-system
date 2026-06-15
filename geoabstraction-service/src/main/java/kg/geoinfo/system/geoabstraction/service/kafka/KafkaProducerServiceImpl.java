package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.common.GeoAnalysisTaskEvent;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.common.GeoVectorExportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private static final String TERRAIN_TOPIC = "geoabstraction.terrain.events";
    private static final String RASTER_TOPIC = "geoabstraction.raster.events";
    private static final String GEO_DATA_TOPIC = "geo.data.events";
    private static final String ANALYSIS_TASKS_TOPIC = "geoabstraction.tasks";
    private static final String VECTOR_EXPORT_TOPIC = "geo.vector.export";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendGeoAbstractJobEvent(GeoAbstractJobEvent event) {
        try {
            String topic = TERRAIN_TOPIC;
            if ("SENTINEL_COG".equals(event.getTaskType()) || 
                "LANDSAT_COG".equals(event.getTaskType()) || 
                "RAW_GEOTIFF_OPTIMIZE".equals(event.getTaskType()) ||
                "TERRAIN_COG".equals(event.getTaskType())) {
                topic = RASTER_TOPIC;
            }
            
            String key = event.getJobId() != null ? event.getJobId().toString() : java.util.UUID.randomUUID().toString();
            kafkaTemplate.send(topic, key, event);
            log.info("Sent {} event to topic {}: {}", event.getEventType(), topic, key);
        } catch (Exception e) {
            log.error("Error sending event to Kafka: {}", e.getMessage());
        }
    }

    @Override
    public void sendGeoObjectEvent(Map<String, Object> payload, kg.geoinfo.system.common.GeoObjectEvent.EventType eventType) {
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
    public void sendGeoAnalysisTaskEvent(GeoAnalysisTaskEvent event) {
        try {
            String key = event.getTaskId().toString();
            kafkaTemplate.send(ANALYSIS_TASKS_TOPIC, key, event);
            log.info("Sent analysis task event to topic {}: {}", ANALYSIS_TASKS_TOPIC, key);
        } catch (Exception e) {
            log.error("Error sending analysis task event to Kafka: {}", e.getMessage());
        }
    }

    @Override
    public void sendVectorExportRequest(GeoVectorExportRequest request) {
        try {
            String key = request.getTaskId().toString();
            kafkaTemplate.send(VECTOR_EXPORT_TOPIC, key, request);
            log.info("Sent vector export request to topic {}: {}", VECTOR_EXPORT_TOPIC, key);
        } catch (Exception e) {
            log.error("Error sending vector export request to Kafka: {}", e.getMessage());
        }
    }
}
