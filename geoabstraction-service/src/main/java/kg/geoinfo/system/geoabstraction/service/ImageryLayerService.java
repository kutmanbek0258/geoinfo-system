package kg.geoinfo.system.geoabstraction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geoabstraction.dto.ImageryLayerDto;
import kg.geoinfo.system.geoabstraction.mapper.ImageryLayerMapper;
import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import kg.geoinfo.system.geoabstraction.repository.ImageryLayerRepository;
import kg.geoinfo.system.geoabstraction.service.geoserver.GeoServerClient;
import kg.geoinfo.system.geoabstraction.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
