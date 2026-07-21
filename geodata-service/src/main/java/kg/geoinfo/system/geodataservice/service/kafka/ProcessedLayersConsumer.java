package kg.geoinfo.system.geodataservice.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.models.*;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import kg.geoinfo.system.geodataservice.service.VectorIngestionService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedLayersConsumer {

    private final ProjectRepository projectRepository;
    private final LayerRepository layerRepository;
    private final ProjectRasterRepository projectRasterRepository;
    private final RasterLayerRepository rasterLayerRepository;
    private final TerrainLayerRepository terrainLayerRepository;
    private final ThreeDTilesLayerRepository threeDTilesLayerRepository;
    private final VectorIngestionService vectorIngestionService;

    @KafkaListener(topics = "geo.vector.processed",
                   containerFactory = "processedLayersListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geodata-service-processed-vector-group}")
    public void listenVectorProcessed(Map<String, Object> payload) {
        log.info("Received geo.vector.processed event: {}", payload);
        try {
            vectorIngestionService.processVectorImport(payload);
        } catch (Exception e) {
            log.error("Failed to process geo.vector.processed event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing geo.vector.processed event", e);
        }
    }

    @KafkaListener(topics = "geo.raster.processed",
                   containerFactory = "processedLayersListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geodata-service-processed-raster-group}")
    public void listenRasterProcessed(Map<String, Object> payload) {

        log.info("Received geo.raster.processed event: {}", payload);
        try {
            UUID id = UUID.fromString((String) payload.get("id"));
            String projectIdStr = (String) payload.get("projectId");

            if (projectIdStr == null) {
                // Non-project raster → save as global RasterLayer
                handleGlobalRasterLayer(id, payload);
            } else {
                // Project raster → save as ProjectRaster
                handleProjectRaster(id, UUID.fromString(projectIdStr), payload);
            }
        } catch (Exception e) {
            log.error("Failed to process geo.raster.processed event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing geo.raster.processed event", e);
        }
    }

    private void handleGlobalRasterLayer(UUID id, Map<String, Object> payload) {
        log.info("Processing as global RasterLayer (no projectId): {}", id);

        RasterLayer rasterLayer = rasterLayerRepository.findById(id).orElse(new RasterLayer());
        rasterLayer.setId(id);
        rasterLayer.setName((String) payload.get("name"));
        rasterLayer.setDescription((String) payload.get("description"));
        rasterLayer.setCogObjectKey((String) payload.get("cogObjectKey"));
        rasterLayer.setCrs((String) payload.get("crs"));
        rasterLayer.setColormapId((String) payload.get("colormapId"));
        rasterLayer.setResampling((String) payload.get("resampling"));

        if (payload.get("dateCaptured") != null) {
            rasterLayer.setDateCaptured(new java.util.Date(((Number) payload.get("dateCaptured")).longValue()));
        } else {
            rasterLayer.setDateCaptured(new java.util.Date());
        }

        rasterLayer.setStatus(Status.COMPLETED);

        Map<String, Object> characteristics = (Map<String, Object>) payload.get("characteristics");
        rasterLayer.setCharacteristics(characteristics != null ? characteristics : new HashMap<>());

        rasterLayer.setBbox(parseBbox((String) payload.get("bbox")));

        rasterLayerRepository.save(rasterLayer);
        log.info("Successfully saved global RasterLayer {}", id);
    }

    private void handleProjectRaster(UUID id, UUID projectId, Map<String, Object> payload) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        // Find or create default RASTER layer
        Layer layer = layerRepository.findAllByProjectId(projectId).stream()
                .filter(l -> l.getType() == LayerType.RASTER)
                .findFirst()
                .orElseGet(() -> {
                    Layer newLayer = Layer.builder()
                            .project(project)
                            .name("Растровые данные")
                            .type(LayerType.RASTER)
                            .build();
                    return layerRepository.save(newLayer);
                });

        ProjectRaster raster = projectRasterRepository.findById(id).orElse(new ProjectRaster());
        raster.setId(id);
        raster.setLayer(layer);
        raster.setName((String) payload.get("name"));
        raster.setDescription((String) payload.get("description"));
        raster.setCogObjectKey((String) payload.get("cogObjectKey"));
        raster.setCrs((String) payload.get("crs"));
        raster.setColormapId((String) payload.get("colormapId"));
        raster.setResampling((String) payload.get("resampling"));

        if (payload.get("dateCaptured") != null) {
            raster.setDateCaptured(new java.util.Date(((Number) payload.get("dateCaptured")).longValue()));
        } else {
            raster.setDateCaptured(new java.util.Date());
        }

        raster.setStatus(Status.COMPLETED);

        Map<String, Object> characteristics = (Map<String, Object>) payload.get("characteristics");
        raster.setCharacteristics(characteristics != null ? characteristics : new HashMap<>());

        raster.setBbox(parseBbox((String) payload.get("bbox")));

        projectRasterRepository.save(raster);
        log.info("Successfully saved/updated ProjectRaster {}", id);
    }
    @KafkaListener(topics = "geo.terrain.processed",
                   containerFactory = "processedLayersListenerContainerFactory",
                   groupId = "${spring.kafka.consumer.group-id:geodata-service-processed-terrain-group}")
    public void listenTerrainProcessed(Map<String, Object> payload) {
        log.info("Received geo.terrain.processed event: {}", payload);
        try {
            Object jobIdObj = payload.get("jobId");
            UUID jobId = (jobIdObj != null && !jobIdObj.toString().trim().isEmpty() && !"null".equalsIgnoreCase(jobIdObj.toString())) 
                    ? UUID.fromString(jobIdObj.toString()) 
                    : null;

            String outputPrefix = (String) payload.get("outputPrefix");
            String name = (String) payload.get("name");
            String terrainUrl = (String) payload.get("terrainUrl");
            String cogObjectKey = (String) payload.get("cogObjectKey");
            String taskType = (String) payload.get("taskType");

            if ("3D_TILES".equals(taskType) || "CITYGML".equals(taskType)) {
                String tilesetUrl = (String) payload.get("tilesetUrl");
                String sourceObjectKey = (String) payload.get("sourceObjectKey");

                ThreeDTilesLayer layer = null;
                if (jobId != null) {
                    layer = threeDTilesLayerRepository.findByJobId(jobId).orElse(null);
                }
                if (layer == null) {
                    layer = new ThreeDTilesLayer();
                }

                layer.setJobId(jobId);
                layer.setOutputPrefix(outputPrefix);
                layer.setTitle(name);
                layer.setTilesetUrl(tilesetUrl != null ? tilesetUrl : "/3dtiles/" + outputPrefix + "/tileset.json");
                layer.setSourceObjectKey(sourceObjectKey);
                layer.setStatus("READY");
                layer.setIsVisible(true);

                threeDTilesLayerRepository.save(layer);
                log.info("Successfully saved/updated ThreeDTilesLayer for jobId: {}", jobId);
            } else {
                TerrainLayer terrainLayer = null;
                if (jobId != null) {
                    terrainLayer = terrainLayerRepository.findByJobId(jobId).orElse(null);
                }
                if (terrainLayer == null) {
                    terrainLayer = new TerrainLayer();
                }

                terrainLayer.setJobId(jobId);
                terrainLayer.setOutputPrefix(outputPrefix);
                terrainLayer.setTitle(name);
                terrainLayer.setTerrainUrl(terrainUrl);
                terrainLayer.setCogObjectKey(cogObjectKey);
                terrainLayer.setStatus("READY");
                terrainLayer.setIsVisible(true);

                terrainLayerRepository.save(terrainLayer);
                log.info("Successfully saved/updated TerrainLayer for jobId: {}", jobId);
            }

        } catch (Exception e) {
            log.error("Failed to process geo.terrain.processed event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing geo.terrain.processed event", e);
        }
    }

    private MultiPolygon parseBbox(String stringBbox) {
        MultiPolygon bbox = null;
        if (stringBbox != null && !stringBbox.trim().isEmpty()) {
            try {
                // Используем WKTReader для чтения WKT-формата
                WKTReader reader = new WKTReader();
                Geometry geom = reader.read(stringBbox);

                if (geom instanceof MultiPolygon) {
                    bbox = (MultiPolygon) geom;
                } else if (geom instanceof Polygon) {
                    bbox = geom.getFactory().createMultiPolygon(new Polygon[]{(Polygon) geom});
                }
            } catch (Exception e) {
                log.error("Failed to convert bbox for job {}: {}", stringBbox, e.getMessage());
            }
        }
        return bbox;
    }
}
