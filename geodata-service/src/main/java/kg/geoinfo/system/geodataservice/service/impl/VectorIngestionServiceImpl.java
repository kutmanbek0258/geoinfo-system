package kg.geoinfo.system.geodataservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.config.MinioProperties;
import kg.geoinfo.system.geodataservice.models.*;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.service.FileStoreService;
import kg.geoinfo.system.geodataservice.service.VectorIngestionService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIngestionServiceImpl implements VectorIngestionService {

    private final ProjectRepository projectRepository;
    private final GeoFolderRepository geoFolderRepository;
    private final LayerRepository layerRepository;
    private final ProjectPointRepository projectPointRepository;
    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectPolygonRepository projectPolygonRepository;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void processVectorImport(Map<String, Object> payload) {
        log.info("Processing vector import event: {}", payload);
        try {
            String projectIdStr = (String) payload.get("projectId");
            if (projectIdStr == null) {
                log.error("Cannot ingest vector data: projectId is null in payload");
                return;
            }

            UUID projectId = UUID.fromString(projectIdStr);
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            GeoFolder folder = null;
            String folderIdStr = (String) payload.get("folderId");
            if (folderIdStr != null && !folderIdStr.isBlank()) {
                folder = geoFolderRepository.findById(UUID.fromString(folderIdStr)).orElse(null);
            }

            String layerName = (String) payload.get("layerName");
            if (layerName == null || layerName.isBlank()) {
                layerName = "Импортированные векторы";
            }

            // 1. Always Create a New Layer
            Layer newLayer = Layer.builder()
                    .project(project)
                    .name(layerName)
                    .type(LayerType.VECTOR)
                    .build();
            Layer layer = layerRepository.save(newLayer);

            String geojsonObjectKey = (String) payload.get("geojsonObjectKey");
            if (geojsonObjectKey == null) {
                log.error("geojsonObjectKey is missing in vector import payload");
                return;
            }

            // 2. Fetch GeoJSON stream from MinIO
            InputStream is = fileStoreService.getFileStream(minioProperties.getBucket(), geojsonObjectKey);
            JsonNode rootNode = objectMapper.readTree(is);
            is.close();

            JsonNode featuresNode = rootNode.get("features");
            if (featuresNode == null || !featuresNode.isArray()) {
                log.warn("GeoJSON has no 'features' array for key {}", geojsonObjectKey);
                return;
            }

            GeoJsonReader geoJsonReader = new GeoJsonReader();

            List<ProjectPoint> pointsToSave = new ArrayList<>();
            List<ProjectMultiline> linesToSave = new ArrayList<>();
            List<ProjectPolygon> polygonsToSave = new ArrayList<>();

            int count = 0;
            for (JsonNode feature : featuresNode) {
                JsonNode geomNode = feature.get("geometry");
                if (geomNode == null || geomNode.isNull()) {
                    continue;
                }

                Geometry jtsGeom;
                try {
                    jtsGeom = geoJsonReader.read(geomNode.toString());
                } catch (Exception ex) {
                    log.warn("Failed to parse feature geometry: {}", ex.getMessage());
                    continue;
                }

                if (jtsGeom == null) continue;

                JsonNode propsNode = feature.get("properties");
                Map<String, Object> propsMap = new HashMap<>();
                String featureName = null;
                if (propsNode != null && propsNode.isObject()) {
                    propsMap = objectMapper.convertValue(propsNode, Map.class);
                    if (propsMap.containsKey("name") && propsMap.get("name") != null) {
                        featureName = String.valueOf(propsMap.get("name"));
                    } else if (propsMap.containsKey("NAME") && propsMap.get("NAME") != null) {
                        featureName = String.valueOf(propsMap.get("NAME"));
                    }
                }
                if (featureName == null) {
                    featureName = layerName + " #" + (count + 1);
                }

                String geomType = jtsGeom.getGeometryType().toUpperCase();

                if (geomType.contains("POINT")) {
                    ProjectPoint point = new ProjectPoint();
                    point.setProject(project);
                    point.setLayer(layer);
                    point.setFolder(folder);
                    point.setName(featureName);
                    point.setStatus(Status.COMPLETED);
                    point.setGeom(GeometryUtils.ensureMultiPoint3D(jtsGeom));
                    point.setCharacteristics(propsMap);
                    pointsToSave.add(point);
                } else if (geomType.contains("LINESTRING")) {
                    ProjectMultiline line = new ProjectMultiline();
                    line.setProject(project);
                    line.setLayer(layer);
                    line.setFolder(folder);
                    line.setName(featureName);
                    line.setStatus(Status.COMPLETED);
                    line.setGeom(GeometryUtils.ensureMultiLineString3D(jtsGeom));
                    line.setCharacteristics(propsMap);
                    linesToSave.add(line);
                } else if (geomType.contains("POLYGON")) {
                    ProjectPolygon poly = new ProjectPolygon();
                    poly.setProject(project);
                    poly.setLayer(layer);
                    poly.setFolder(folder);
                    poly.setName(featureName);
                    poly.setStatus(Status.COMPLETED);
                    poly.setGeom(GeometryUtils.ensureMultiPolygon3D(jtsGeom));
                    poly.setCharacteristics(propsMap);
                    polygonsToSave.add(poly);
                }

                count++;

                // Batch insert every 500 records
                if (pointsToSave.size() >= 500) {
                    projectPointRepository.saveAll(pointsToSave);
                    pointsToSave.clear();
                }
                if (linesToSave.size() >= 500) {
                    projectMultilineRepository.saveAll(linesToSave);
                    linesToSave.clear();
                }
                if (polygonsToSave.size() >= 500) {
                    projectPolygonRepository.saveAll(polygonsToSave);
                    polygonsToSave.clear();
                }
            }

            // Flush remaining
            if (!pointsToSave.isEmpty()) {
                projectPointRepository.saveAll(pointsToSave);
            }
            if (!linesToSave.isEmpty()) {
                projectMultilineRepository.saveAll(linesToSave);
            }
            if (!polygonsToSave.isEmpty()) {
                projectPolygonRepository.saveAll(polygonsToSave);
            }

            log.info("Successfully ingested {} vector features into project {} (Points: {}, Lines: {}, Polygons: {})",
                    count, projectId, pointsToSave.size(), linesToSave.size(), polygonsToSave.size());

            // Notify search indexing
            Map<String, Object> searchPayload = new HashMap<>();
            searchPayload.put("id", layer.getId());
            searchPayload.put("projectId", projectId);
            searchPayload.put("layerName", layerName);
            searchPayload.put("featureCount", count);
            kafkaProducerService.sendGeoObjectEvent(searchPayload, kg.geoinfo.system.common.GeoObjectEvent.EventType.CREATED);

        } catch (Exception e) {
            log.error("Failed to process vector import: {}", e.getMessage(), e);
            throw new RuntimeException("Error ingesting vector data", e);
        }
    }
}
