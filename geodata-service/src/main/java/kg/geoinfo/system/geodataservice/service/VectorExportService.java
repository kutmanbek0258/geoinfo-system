package kg.geoinfo.system.geodataservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoVectorExportRequest;
import kg.geoinfo.system.common.GeoVectorExportResponse;
import kg.geoinfo.system.geodataservice.config.MinioProperties;
import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import kg.geoinfo.system.geodataservice.models.ProjectPolygon;
import kg.geoinfo.system.geodataservice.repository.ProjectMultilineRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectPointRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectPolygonRepository;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorExportService {

    private final ProjectPointRepository pointRepository;
    private final ProjectMultilineRepository multilineRepository;
    private final ProjectPolygonRepository polygonRepository;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public void exportToGeoJson(GeoVectorExportRequest request) {
        try {
            log.info("Exporting vector data for task {} (layerId: {})", request.getTaskId(), request.getLayerId());
            
            Map<String, Object> featureCollection = new HashMap<>();
            featureCollection.put("type", "FeatureCollection");
            List<Map<String, Object>> features = new ArrayList<>();

            // Search in points, lines, and polygons (and folders in a real scenario)
            // For simplicity, we search all entities belonging to this folder/id
            // In geodata-service, folders can contain multiple types.
            
            // Check if lists of specific IDs are provided
            boolean hasPointIds = request.getPointIds() != null && !request.getPointIds().isEmpty();
            boolean hasMultilineIds = request.getMultilineIds() != null && !request.getMultilineIds().isEmpty();
            boolean hasPolygonIds = request.getPolygonIds() != null && !request.getPolygonIds().isEmpty();

            if (hasPointIds || hasMultilineIds || hasPolygonIds) {
                log.info("Exporting specific vector IDs for task {}: points={}, lines={}, polygons={}",
                        request.getTaskId(),
                        hasPointIds ? request.getPointIds().size() : 0,
                        hasMultilineIds ? request.getMultilineIds().size() : 0,
                        hasPolygonIds ? request.getPolygonIds().size() : 0);
                
                if (hasPointIds) {
                    pointRepository.findAllById(request.getPointIds()).forEach(p -> features.add(convertToFeature(p)));
                }
                if (hasMultilineIds) {
                    multilineRepository.findAllById(request.getMultilineIds()).forEach(l -> features.add(convertToFeature(l)));
                }
                if (hasPolygonIds) {
                    polygonRepository.findAllById(request.getPolygonIds()).forEach(p -> features.add(convertToFeature(p)));
                }
            } else {
                // Fetch geometries (either by folder ID or root project geometries)
                if (request.getLayerId() == null || request.getLayerId().equals(new UUID(0L, 0L))) {
                    pointRepository.findAllByProjectIdAndFolderIdIsNull(request.getProjectId()).forEach(p -> features.add(convertToFeature(p)));
                    multilineRepository.findAllByProjectIdAndFolderIdIsNull(request.getProjectId()).forEach(l -> features.add(convertToFeature(l)));
                    polygonRepository.findAllByProjectIdAndFolderIdIsNull(request.getProjectId()).forEach(p -> features.add(convertToFeature(p)));
                } else {
                    pointRepository.findAllByFolderId(request.getLayerId()).forEach(p -> features.add(convertToFeature(p)));
                    multilineRepository.findAllByFolderId(request.getLayerId()).forEach(l -> features.add(convertToFeature(l)));
                    polygonRepository.findAllByFolderId(request.getLayerId()).forEach(p -> features.add(convertToFeature(p)));
                }
            }

            featureCollection.put("features", features);

            String geojson = objectMapper.writeValueAsString(featureCollection);
            byte[] bytes = geojson.getBytes(StandardCharsets.UTF_8);

            fileStoreService.uploadFile(
                    minioProperties.getBucket(),
                    request.getS3Destination(),
                    new ByteArrayInputStream(bytes),
                    "application/geo+json"
            );

            kafkaProducerService.sendGeoVectorExportResponse(GeoVectorExportResponse.builder()
                    .taskId(request.getTaskId())
                    .exportKey(request.getExportKey())
                    .success(true)
                    .s3Url("s3://" + minioProperties.getBucket() + "/" + request.getS3Destination())
                    .build());

        } catch (Exception e) {
            log.error("Failed to export vector data for task {}: {}", request.getTaskId(), e.getMessage());
            kafkaProducerService.sendGeoVectorExportResponse(GeoVectorExportResponse.builder()
                    .taskId(request.getTaskId())
                    .exportKey(request.getExportKey())
                    .success(false)
                    .error(e.getMessage())
                    .build());
        }
    }

    private Map<String, Object> convertToFeature(Object entity) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");
        
        Map<String, Object> properties = new HashMap<>();
        if (entity instanceof ProjectPoint p) {
            feature.put("geometry", p.getGeom());
            properties.put("id", p.getId());
            properties.put("name", p.getName());
            // Add other properties if needed
        } else if (entity instanceof ProjectMultiline l) {
            feature.put("geometry", l.getGeom());
            properties.put("id", l.getId());
            properties.put("name", l.getName());
        } else if (entity instanceof ProjectPolygon poly) {
            feature.put("geometry", poly.getGeom());
            properties.put("id", poly.getId());
            properties.put("name", poly.getName());
        }
        
        feature.put("properties", properties);
        return feature;
    }
}
