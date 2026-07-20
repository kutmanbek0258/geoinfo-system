package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.config.MinioProperties;
import kg.geoinfo.system.geodataservice.dto.ThreeDTilesLayerDto;
import kg.geoinfo.system.geodataservice.mapper.ThreeDTilesLayerMapper;
import kg.geoinfo.system.geodataservice.models.ThreeDTilesLayer;
import kg.geoinfo.system.geodataservice.repository.ThreeDTilesLayerRepository;
import kg.geoinfo.system.geodataservice.service.FileStoreService;
import kg.geoinfo.system.geodataservice.service.ThreeDTilesLayerService;
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
public class ThreeDTilesLayerServiceImpl implements ThreeDTilesLayerService {

    private final ThreeDTilesLayerRepository threeDTilesLayerRepository;
    private final ThreeDTilesLayerMapper threeDTilesLayerMapper;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;
    private final KafkaProducerService kafkaProducerService;

    @Override
    public Page<ThreeDTilesLayerDto> getLayers(Pageable pageable) {
        log.info("Fetching all 3D Tiles layers");
        return threeDTilesLayerRepository.findAll(pageable)
                .map(threeDTilesLayerMapper::toDto);
    }

    @Override
    public ThreeDTilesLayerDto getById(UUID id) {
        log.info("Fetching 3D Tiles layer by id: {}", id);
        return threeDTilesLayerRepository.findById(id)
                .map(threeDTilesLayerMapper::toDto)
                .orElseThrow(() -> new RuntimeException("ThreeDTilesLayer not found: " + id));
    }

    @Override
    @Transactional
    public ThreeDTilesLayerDto create(ThreeDTilesLayerDto dto) {
        log.info("Creating 3D Tiles layer: {}", dto.getTitle());
        ThreeDTilesLayer entity = threeDTilesLayerMapper.toEntity(dto);
        return threeDTilesLayerMapper.toDto(threeDTilesLayerRepository.save(entity));
    }

    @Override
    @Transactional
    public ThreeDTilesLayerDto update(UUID id, ThreeDTilesLayerDto dto) {
        log.info("Updating 3D Tiles layer: {}", id);
        ThreeDTilesLayer entity = threeDTilesLayerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ThreeDTilesLayer not found: " + id));
        
        threeDTilesLayerMapper.update(entity, dto);
        return threeDTilesLayerMapper.toDto(threeDTilesLayerRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting 3D Tiles layer: {}", id);
        ThreeDTilesLayer layer = threeDTilesLayerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ThreeDTilesLayer not found: " + id));

        // 1. Delete source file from MinIO if exists
        if (layer.getSourceObjectKey() != null && !layer.getSourceObjectKey().isEmpty()) {
            try {
                fileStoreService.deleteFile(minioProperties.getBucket(), layer.getSourceObjectKey());
                log.info("Successfully deleted source file from MinIO: {}", layer.getSourceObjectKey());
            } catch (Exception e) {
                log.error("Failed to delete source file from MinIO: key={}, error={}", layer.getSourceObjectKey(), e.getMessage());
            }
        }

        // 2. Send DELETED event to Kafka for terrain-worker local files cleanup
        if (layer.getJobId() != null && layer.getOutputPrefix() != null) {
            try {
                kg.geoinfo.system.common.GeoAbstractJobEvent event = kg.geoinfo.system.common.GeoAbstractJobEvent.builder()
                        .jobId(layer.getJobId())
                        .eventType(kg.geoinfo.system.common.GeoAbstractJobEvent.EventType.DELETED)
                        .taskType("3D_TILES")
                        .outputPrefix(layer.getOutputPrefix())
                        .build();

                kafkaProducerService.sendGeoAbstractJobEvent(event);
                log.info("Sent DELETED event to Kafka for job: {}, outputPrefix: {}", layer.getJobId(), layer.getOutputPrefix());
            } catch (Exception e) {
                log.error("Failed to send DELETED event to Kafka for 3D Tiles layer {}: {}", id, e.getMessage());
            }
        }

        // 3. Delete from DB
        threeDTilesLayerRepository.delete(layer);
        log.info("Successfully deleted ThreeDTilesLayer record: {}", id);
    }
}
