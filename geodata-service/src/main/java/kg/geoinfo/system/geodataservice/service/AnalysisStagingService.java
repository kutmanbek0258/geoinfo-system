package kg.geoinfo.system.geodataservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import kg.geoinfo.system.geodataservice.models.TempAnalysisGeometry;
import kg.geoinfo.system.geodataservice.repository.TempAnalysisGeometryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisStagingService {

    private final TempAnalysisGeometryRepository repository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void importSpatialResult(UUID taskId, String s3Url) {
        try {
            log.info("Importing spatial result for task {} from {}", taskId, s3Url);

            // Parse S3 URL: s3://bucket/key
            String path = s3Url.replace("s3://", "");
            int slashIndex = path.indexOf("/");
            String bucket = path.substring(0, slashIndex);
            String key = path.substring(slashIndex + 1);

            try (InputStream is = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build())) {

                JsonNode root = objectMapper.readTree(is);
                if (!root.has("features")) {
                    log.warn("No features found in GeoJSON for task {}", taskId);
                    return;
                }

                List<TempAnalysisGeometry> geometries = new ArrayList<>();
                for (JsonNode feature : root.get("features")) {
                    Geometry geom = objectMapper.convertValue(feature.get("geometry"), Geometry.class);
                    Map<String, Object> props = objectMapper.convertValue(feature.get("properties"), Map.class);

                    TempAnalysisGeometry tempGeom = new TempAnalysisGeometry();
                    tempGeom.setTaskId(taskId);
                    tempGeom.setGeom(GeometryUtils.ensure3D(geom));
                    tempGeom.setProperties(props);
                    tempGeom.setCreatedAt(OffsetDateTime.now());
                    geometries.add(tempGeom);
                }

                repository.deleteByTaskId(taskId); // Clean up previous results for same task if any
                repository.saveAll(geometries);
                log.info("Imported {} geometries for task {}", geometries.size(), taskId);
            }
        } catch (Exception e) {
            log.error("Failed to import spatial result for task {}: {}", taskId, e.getMessage());
        }
    }

    /**
     * Сохраняет S3-ссылку на растровый результат (COG GeoTIFF) как staging-запись.
     * Используется для плагинов типа clip_raster_by_mask, которые возвращают растр, а не вектор.
     * Фронтенд получает URL через GET /api/geodata/staging/{taskId}/raster
     * и отображает слой через presigned URL MinIO или GeoServer.
     */
    @Transactional
    public void importRasterResult(UUID taskId, String s3Url) {
        try {
            log.info("Saving raster staging result for task {} from {}", taskId, s3Url);

            // Сохраняем одну запись-маркер без геометрии, но с метаданными о растре
            Map<String, Object> props = new HashMap<>();
            props.put("type", "RASTER");
            props.put("s3Url", s3Url);

            TempAnalysisGeometry tempGeom = new TempAnalysisGeometry();
            tempGeom.setTaskId(taskId);
            tempGeom.setGeom(null); // Нет геометрии — это растр
            tempGeom.setProperties(props);
            tempGeom.setCreatedAt(OffsetDateTime.now());

            repository.deleteByTaskId(taskId);
            repository.save(tempGeom);
            log.info("Saved raster staging result for task {}: {}", taskId, s3Url);
        } catch (Exception e) {
            log.error("Failed to save raster staging result for task {}: {}", taskId, e.getMessage());
        }
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupOldData() {
        OffsetDateTime expiryTime = OffsetDateTime.now().minusHours(2);
        log.info("Cleaning up temporary analysis data older than {}", expiryTime);
        repository.deleteByCreatedAtBefore(expiryTime);
    }
}
