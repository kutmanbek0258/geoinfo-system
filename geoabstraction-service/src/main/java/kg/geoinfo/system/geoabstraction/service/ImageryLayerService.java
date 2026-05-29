package kg.geoinfo.system.geoabstraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geoabstraction.dto.ImageryLayerDto;
import kg.geoinfo.system.geoabstraction.mapper.ImageryLayerMapper;
import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import kg.geoinfo.system.geoabstraction.repository.ImageryLayerRepository;
import kg.geoinfo.system.geoabstraction.config.MinioProperties;
import kg.geoinfo.system.geoabstraction.service.filestore.FileStoreService;
import kg.geoinfo.system.geoabstraction.service.geoserver.GeoServerClient;
import kg.geoinfo.system.geoabstraction.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ImageryLayerService {
    private final ImageryLayerRepository repository;
    private final ImageryLayerMapper imageryLayerMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    private final GeoServerClient geoServerClient;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;

    public ImageryLayerDto save(ImageryLayerDto imageryLayerDto) {
        ImageryLayer entity = imageryLayerMapper.toEntity(imageryLayerDto);
        return save(entity);
    }
    
    public List<String> getStyles() {
        return geoServerClient.getStyles();
    }

    public ImageryLayerDto save(ImageryLayer entity) {
        entity = repository.save(entity);
        sendKafkaEvent(entity, GeoObjectEvent.EventType.CREATED);
        return imageryLayerMapper.toDto(entity);
    }

    public void deleteById(UUID id) {
        ImageryLayer entity = repository.findById(id).orElseThrow(() -> new RuntimeException("ImageryLayer not found"));
        
        // 1. Set status to DELETING
        entity.setStatus(kg.geoinfo.system.geoabstraction.models.enums.Status.DELETING);
        repository.save(entity);
        sendKafkaEvent(entity, GeoObjectEvent.EventType.UPDATED);

        // 2. Delete from GeoServer
        geoServerClient.deleteLayer(entity.getWorkspace(), entity.getLayerName());
        geoServerClient.deleteCoverageStore(entity.getWorkspace(), entity.getLayerName()); // layerName is storeName in our convention

        // 3. Send event to Kafka for geoabstract-worker to delete file
        log.info("Sending DELETED event for imagery layer {} (jobId: {})", id, entity.getJobId());
        kg.geoinfo.system.common.GeoAbstractJobEvent event = kg.geoinfo.system.common.GeoAbstractJobEvent.builder()
                .jobId(entity.getJobId())
                .eventType(kg.geoinfo.system.common.GeoAbstractJobEvent.EventType.DELETED)
                .taskType("RAW_GEOTIFF_OPTIMIZE") // worker uses this for raster cleanup
                .outputPrefix(entity.getLayerName())
                .build();

        kafkaProducerService.sendGeoAbstractJobEvent(event);

        // Fallback for legacy data: if jobId is null, we can't wait for confirmation.
        // Delete from DB immediately.
        if (entity.getJobId() == null) {
            log.info("Legacy imagery layer without jobId detected, force deleting from DB immediately.");
            forceDelete(id);
        }
    }

    public void forceDelete(UUID id) {
        log.info("Finalizing deletion for imagery layer {}", id);
        repository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id, "type", "imagery");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }

    public ImageryLayerDto findById(UUID id) {
        return imageryLayerMapper.toDto(repository.findById(id).orElseThrow());
    }

    public Page<ImageryLayerDto> findByCondition(ImageryLayerDto imageryLayerDto, Pageable pageable) {
        // Simple implementation: if name is provided, filter by name. Otherwise return all.
        // In a real app, this should use Specification or Querydsl.
        Page<ImageryLayer> entityPage;
        if (imageryLayerDto.getName() != null && !imageryLayerDto.getName().isEmpty()) {
            entityPage = repository.findAllByNameContainingIgnoreCase(imageryLayerDto.getName(), pageable);
        } else {
            entityPage = repository.findAll(pageable);
        }
        List<ImageryLayer> entities = entityPage.getContent();
        return new PageImpl<>(imageryLayerMapper.toDto(entities), pageable, entityPage.getTotalElements());
    }

    public ImageryLayerDto update(ImageryLayerDto imageryLayerDto, UUID id) {
        ImageryLayer entity = repository.findById(id).orElseThrow(() -> new RuntimeException("ImageryLayer not found"));
        imageryLayerMapper.update(entity, imageryLayerDto);
        geoServerClient.updateLayerStyle(entity.getLayerName(), entity.getStyle());
        entity = repository.save(entity);
        sendKafkaEvent(entity, GeoObjectEvent.EventType.UPDATED);
        return imageryLayerMapper.toDto(entity);
    }

    private void sendKafkaEvent(ImageryLayer entity, GeoObjectEvent.EventType eventType) {
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        payload.put("type", "imagery");
        kafkaProducerService.sendGeoObjectEvent(payload, eventType);
    }

    public String generateImageryPresignedUrl(UUID layerId) {
        ImageryLayer layer = repository.findById(layerId)
                .orElseThrow(() -> new RuntimeException("ImageryLayer not found: " + layerId));

        if (layer.getCogObjectKey() == null) {
            throw new RuntimeException("COG object key not found for imagery layer: " + layerId);
        }

        String url = fileStoreService.generateDownloadUrl(layer.getCogObjectKey());

        // Rewrite internal MinIO URL to public /imagery/cog/ prefix
        // From: http://minio:9000/geo-abstraction-input/imagery-cog/uuid.tif?X-Amz...
        // To:   /imagery/cog/uuid.tif?X-Amz...
        String bucketPath = "/" + minioProperties.getBucket() + "/imagery-cog/";
        if (url.contains(bucketPath)) {
            url = "/imagery/cog/" + url.split(bucketPath)[1];
        }

        return url;
    }
}
