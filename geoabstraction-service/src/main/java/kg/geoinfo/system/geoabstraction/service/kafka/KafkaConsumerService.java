package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.geoabstraction.service.GeoAbstractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final GeoAbstractionService geoAbstractionService;
    private final kg.geoinfo.system.geoabstraction.service.ImageryLayerService imageryLayerService;
    private final kg.geoinfo.system.geoabstraction.repository.ImageryLayerRepository imageryLayerRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"geoabstraction.terrain.events", "geoabstraction.raster.events"}, 
                   groupId = "${spring.kafka.consumer.group-id:geoabstraction-service-group}")
    public void listen(GeoAbstractJobEvent event) {
        log.info("Received geo-abstraction job event: {} for job {} (Type: {})", 
                event.getEventType(), event.getJobId(), event.getTaskType());

        if (event.getEventType() == GeoAbstractJobEvent.EventType.READY || event.getEventType() == GeoAbstractJobEvent.EventType.FAILED) {
            String terrainUrl = event.getTerrainUrl();
            if (terrainUrl == null && event.getEventType() == GeoAbstractJobEvent.EventType.READY && "TERRAIN_MESH".equals(event.getTaskType())) {
                terrainUrl = "/terrain/" + event.getOutputPrefix() + "/";
            }

            MultiPolygon bbox = null;
            if (event.getBbox() != null) {
                try {
                    Geometry geom = objectMapper.convertValue(event.getBbox(), Geometry.class);
                    if (geom instanceof MultiPolygon) {
                        bbox = (MultiPolygon) geom;
                    } else if (geom instanceof Polygon) {
                        bbox = geom.getFactory().createMultiPolygon(new Polygon[]{(Polygon) geom});
                    }
                } catch (Exception e) {
                    log.error("Failed to convert bbox for job {}: {}", event.getJobId(), e.getMessage());
                }
            }

            geoAbstractionService.updateJobStatus(
                    event.getJobId(),
                    event.getEventType().name(),
                    event.getErrorMessage(),
                    null,
                    null,
                    terrainUrl,
                    event.getCogObjectKey(),
                    event.getTaskType(),
                    bbox
            );
        } else if (event.getEventType() == GeoAbstractJobEvent.EventType.DELETED) {
            // Confirmation from worker that files are deleted
            imageryLayerRepository.findByJobId(event.getJobId()).ifPresent(layer -> {
                imageryLayerService.forceDelete(layer.getId());
            });
        }
    }
}
