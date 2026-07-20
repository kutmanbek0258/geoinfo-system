package kg.geoinfo.system.geoabstraction.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.common.GeoAnalysisResultEvent;
import kg.geoinfo.system.common.GeoVectorExportResponse;
import kg.geoinfo.system.geoabstraction.service.AnalysisTaskService;
import kg.geoinfo.system.geoabstraction.service.GeoAbstractionService;
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
    private final AnalysisTaskService analysisTaskService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"geoabstraction.terrain.events", "geoabstraction.raster.events"}, 
                   groupId = "${spring.kafka.consumer.group-id:geoabstraction-service-group}")
    public void listen(GeoAbstractJobEvent event) {
        log.info("Received geo-abstraction job event: {} for job {} (Type: {})", 
                event.getEventType(), event.getJobId(), event.getTaskType());

        if (event.getEventType() == GeoAbstractJobEvent.EventType.READY || event.getEventType() == GeoAbstractJobEvent.EventType.FAILED) {
            String terrainUrl = event.getTerrainUrl();
            if (terrainUrl == null && event.getEventType() == GeoAbstractJobEvent.EventType.READY) {
                if ("TERRAIN_MESH".equals(event.getTaskType())) {
                    terrainUrl = "/terrain/" + event.getOutputPrefix() + "/";
                } else if ("3D_TILES".equals(event.getTaskType()) || "CITYGML".equals(event.getTaskType())) {
                    terrainUrl = "/3dtiles/" + event.getOutputPrefix() + "/tileset.json";
                }
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
                    bbox,
                    event.getCharacteristics()
            );
        } else if (event.getEventType() == GeoAbstractJobEvent.EventType.DELETED) {
            // Confirmation from worker that files are deleted
            log.info("Job {} files deleted from S3", event.getJobId());
        }
    }

    @KafkaListener(topics = "geoabstraction.results", 
                   containerFactory = "analysisResultsListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geoabstraction-service-group}")
    public void listenAnalysisResults(GeoAnalysisResultEvent event) {
        log.info("Received geo-analysis result: {} for task {}", event.getStatus(), event.getTaskId());
        analysisTaskService.handleAnalysisResult(event);
    }

    @KafkaListener(topics = "geo.vector.export.results", 
                   containerFactory = "vectorExportListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geoabstraction-service-group}")
    public void listenExportResults(GeoVectorExportResponse response) {
        log.info("Received vector export response: success={} for task {}", response.isSuccess(), response.getTaskId());
        analysisTaskService.handleExportResponse(response);
    }
}
