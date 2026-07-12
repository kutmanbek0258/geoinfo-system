package kg.geoinfo.system.geodataservice.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.models.Layer;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectRaster;
import kg.geoinfo.system.geodataservice.models.RasterLayer;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.LayerRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRasterRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.repository.RasterLayerRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedLayersConsumer {

    private final ProjectRepository projectRepository;
    private final LayerRepository layerRepository;
    private final ProjectRasterRepository projectRasterRepository;
    private final RasterLayerRepository rasterLayerRepository;
    private final ObjectMapper objectMapper;

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
            UUID id = UUID.fromString((String) payload.get("id"));
            UUID projectId = UUID.fromString((String) payload.get("projectId"));

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
            raster.setDescription("Processed terrain DEM layer");
            raster.setCogObjectKey((String) payload.get("cogObjectKey"));
            raster.setCrs("EPSG:4326");
            raster.setResampling("bilinear");
            raster.setDateCaptured(new java.util.Date());
            raster.setStatus(Status.COMPLETED);

            Map<String, Object> characteristics = new HashMap<>();
            characteristics.put("isTerrain", true);
            characteristics.put("terrainUrl", payload.get("terrainUrl"));
            raster.setCharacteristics(characteristics);

            projectRasterRepository.save(raster);
            log.info("Successfully saved/updated ProjectRaster (Terrain) {}", id);

        } catch (Exception e) {
            log.error("Failed to process geo.terrain.processed event: {}", e.getMessage(), e);
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
