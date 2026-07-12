package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.config.MinioProperties;
import kg.geoinfo.system.geodataservice.dto.TerrainLayerDto;
import kg.geoinfo.system.geodataservice.mapper.TerrainLayerMapper;
import kg.geoinfo.system.geodataservice.models.TerrainLayer;
import kg.geoinfo.system.geodataservice.repository.TerrainLayerRepository;
import kg.geoinfo.system.geodataservice.service.FileStoreService;
import kg.geoinfo.system.geodataservice.service.TerrainLayerService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerrainLayerServiceImpl implements TerrainLayerService {

    private final TerrainLayerRepository terrainLayerRepository;
    private final TerrainLayerMapper terrainLayerMapper;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;
    private final KafkaProducerService kafkaProducerService;

    @Override
    public Page<TerrainLayerDto> getLayers(Pageable pageable) {
        log.info("Fetching all terrain layers");
        return terrainLayerRepository.findAll(pageable)
                .map(terrainLayerMapper::toDto);
    }

    @Override
    public TerrainLayerDto getById(UUID id) {
        log.info("Fetching terrain layer by id: {}", id);
        return terrainLayerRepository.findById(id)
                .map(terrainLayerMapper::toDto)
                .orElseThrow(() -> new RuntimeException("TerrainLayer not found: " + id));
    }

    @Override
    @Transactional
    public TerrainLayerDto create(TerrainLayerDto dto) {
        log.info("Creating terrain layer: {}", dto.getTitle());
        TerrainLayer entity = terrainLayerMapper.toEntity(dto);
        return terrainLayerMapper.toDto(terrainLayerRepository.save(entity));
    }

    @Override
    @Transactional
    public TerrainLayerDto update(UUID id, TerrainLayerDto dto) {
        log.info("Updating terrain layer: {}", id);
        TerrainLayer entity = terrainLayerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TerrainLayer not found: " + id));
        
        terrainLayerMapper.update(entity, dto);
        return terrainLayerMapper.toDto(terrainLayerRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting terrain layer: {}", id);
        TerrainLayer layer = terrainLayerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TerrainLayer not found: " + id));

        // 1. Delete COG file from MinIO if it exists
        if (layer.getCogObjectKey() != null && !layer.getCogObjectKey().isEmpty()) {
            try {
                fileStoreService.deleteFile(minioProperties.getBucket(), layer.getCogObjectKey());
                log.info("Successfully deleted terrain COG from MinIO: {}", layer.getCogObjectKey());
            } catch (Exception e) {
                log.error("Failed to delete terrain COG file from MinIO: key={}, error={}", layer.getCogObjectKey(), e.getMessage());
            }
        }

        // 2. Send DELETED event to Kafka for terrain-worker local files cleanup
        if (layer.getJobId() != null && layer.getOutputPrefix() != null) {
            try {
                kg.geoinfo.system.common.GeoAbstractJobEvent event = kg.geoinfo.system.common.GeoAbstractJobEvent.builder()
                        .jobId(layer.getJobId())
                        .eventType(kg.geoinfo.system.common.GeoAbstractJobEvent.EventType.DELETED)
                        .taskType("TERRAIN_MESH")
                        .outputPrefix(layer.getOutputPrefix())
                        .build();

                kafkaProducerService.sendGeoAbstractJobEvent(event);
                log.info("Sent DELETED event to Kafka for job: {}, outputPrefix: {}", layer.getJobId(), layer.getOutputPrefix());
            } catch (Exception e) {
                log.error("Failed to send DELETED event to Kafka for terrain layer {}: {}", id, e.getMessage());
            }
        }

        // 3. Delete from DB
        terrainLayerRepository.delete(layer);
        log.info("Successfully deleted TerrainLayer record: {}", id);
    }

    @Override
    public String generateTerrainPresignedUrl(UUID layerId) {
        TerrainLayer layer = terrainLayerRepository.findById(layerId)
                .orElseThrow(() -> new RuntimeException("Layer not found: " + layerId));

        if (layer.getCogObjectKey() == null) {
            throw new RuntimeException("COG object key not found for layer: " + layerId);
        }

        String url = fileStoreService.generateDownloadUrl(layer.getCogObjectKey());

        String bucketPath = "/" + minioProperties.getBucket() + "/terrain-cog/";
        if (url.contains(bucketPath)) {
            url = "/terrain/cog/" + url.split(bucketPath)[1];
        } else if (url.contains("/" + minioProperties.getBucket() + "/")) {
            url = "/minio/" + url.split("/" + minioProperties.getBucket() + "/")[1];
        }

        return url;
    }
}
