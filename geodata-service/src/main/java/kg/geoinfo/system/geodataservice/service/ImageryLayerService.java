
package kg.geoinfo.system.geodataservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.dto.ImageryLayerDto;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.mapper.ImageryLayerMapper;
import kg.geoinfo.system.geodataservice.models.ImageryLayer;
import kg.geoinfo.system.geodataservice.repository.ImageryLayerRepository;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
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

    public ImageryLayerDto save(ImageryLayerDto imageryLayerDto) {
        ImageryLayer entity = imageryLayerMapper.toEntity(imageryLayerDto);
        entity = repository.save(entity);
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);
        return imageryLayerMapper.toDto(entity);
    }

    public void deleteById(UUID id) {
        repository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }

    public ImageryLayerDto findById(UUID id) {
        return imageryLayerMapper.toDto(repository.findById(id).orElseThrow());
    }

    public Page<ImageryLayerDto> findByCondition(ImageryLayerDto imageryLayerDto, Pageable pageable) {
        Page<ImageryLayer> entityPage = repository.findAll(pageable);
        List<ImageryLayer> entities = entityPage.getContent();
        return new PageImpl<>(imageryLayerMapper.toDto(entities), pageable, entityPage.getTotalElements());
    }

    public ImageryLayerDto update(ImageryLayerDto imageryLayerDto, UUID id) {
        ImageryLayer entity = repository.findById(id).orElseThrow(() -> new RuntimeException("ImageryLayer not found"));
        imageryLayerMapper.update(entity, imageryLayerDto);
        entity = repository.save(entity);
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);
        return imageryLayerMapper.toDto(entity);
    }
}
