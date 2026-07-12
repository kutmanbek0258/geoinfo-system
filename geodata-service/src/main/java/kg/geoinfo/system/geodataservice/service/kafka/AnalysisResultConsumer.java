package kg.geoinfo.system.geodataservice.service.kafka;

import kg.geoinfo.system.common.GeoAnalysisResultEvent;
import kg.geoinfo.system.common.GeoVectorExportRequest;
import kg.geoinfo.system.geodataservice.service.AnalysisStagingService;
import kg.geoinfo.system.geodataservice.service.VectorExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisResultConsumer {

    private final AnalysisStagingService analysisStagingService;
    private final VectorExportService vectorExportService;

    @KafkaListener(topics = "geoabstraction.results", 
                   containerFactory = "analysisResultsListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geodata-service-analysis-group}")
    public void listen(GeoAnalysisResultEvent event) {
        log.info("Received geo-analysis result for task {}: {}", event.getTaskId(), event.getStatus());
        
        if (!"COMPLETED".equals(event.getStatus()) || event.getOutputs() == null) {
            return;
        }

        // Случай 1: плагины, возвращающие вектор (GeoJSON) — terrain_contours, zonal_statistics и др.
        String vectorResult = event.getOutputs().get("vector_result");
        if (vectorResult != null) {
            log.info("Importing vector staging result for task {}", event.getTaskId());
            analysisStagingService.importSpatialResult(event.getTaskId(), vectorResult);
            return;
        }

        // Случай 2: плагины, возвращающие растр (COG GeoTIFF) — clip_raster_by_mask и др.
        String rasterResult = event.getOutputs().get("raster_result");
        if (rasterResult != null) {
            log.info("Importing raster staging result for task {}", event.getTaskId());
            analysisStagingService.importRasterResult(event.getTaskId(), rasterResult);
            return;
        }

        log.warn("Task {} completed but no known output key found in outputs: {}", 
                 event.getTaskId(), event.getOutputs().keySet());
    }

    @KafkaListener(topics = "geo.vector.export", 
                   containerFactory = "vectorExportListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geodata-service-export-group}")
    public void listenExportRequest(GeoVectorExportRequest request) {
        log.info("Received vector export request for task {}", request.getTaskId());
        vectorExportService.exportToGeoJson(request);
    }
}
