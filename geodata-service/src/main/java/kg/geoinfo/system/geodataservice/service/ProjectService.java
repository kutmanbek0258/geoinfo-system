
package kg.geoinfo.system.geodataservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.dto.kafka.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.mapper.ProjectMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
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
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectMapper projectMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public ProjectDto save(ProjectDto projectDto) {
        Project entity = projectMapper.toEntity(projectDto);
        entity = repository.save(entity);
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);
        return projectMapper.toDto(entity);
    }

    public void deleteById(UUID id) {
        repository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }

    public ProjectDto findById(UUID id) {
        return projectMapper.toDto(repository.findById(id).orElseThrow());
    }

    public Page<ProjectDto> findByCondition(ProjectDto projectDto, Pageable pageable) {
        Page<Project> entityPage = repository.findAll(pageable);
        List<Project> entities = entityPage.getContent();
        return new PageImpl<>(projectMapper.toDto(entities), pageable, entityPage.getTotalElements());
    }

    public ProjectDto update(ProjectDto projectDto, UUID id) {
        Project entity = repository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        projectMapper.update(entity, projectDto);
        entity = repository.save(entity);
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);
        return projectMapper.toDto(entity);
    }
}
