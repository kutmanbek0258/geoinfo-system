package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPolygonDto;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectPolygonMapper;
import kg.geoinfo.system.geodataservice.models.ProjectPolygon;
import kg.geoinfo.system.geodataservice.repository.ProjectPolygonRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPolygonService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectPolygonServiceImpl implements ProjectPolygonService {

    private final ProjectPolygonRepository projectPolygonRepository;
    private final ProjectPolygonMapper projectPolygonMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProjectPolygonDto create(CreateProjectPolygonDto createProjectPolygonDto) {
        ProjectPolygon projectPolygon = projectPolygonMapper.toEntity(createProjectPolygonDto);
        projectPolygon = projectPolygonRepository.save(projectPolygon);

        Map<String, Object> payload = objectMapper.convertValue(projectPolygon, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);

        return projectPolygonMapper.toDto(projectPolygon);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPolygonDto findById(UUID id) {
        return projectPolygonRepository.findById(id)
                .map(projectPolygonMapper::toDto)
                .orElseThrow(() -> new RuntimeException("ProjectPolygon not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectPolygonDto> findAll(Pageable pageable) {
        return projectPolygonRepository.findAll(pageable)
                .map(projectPolygonMapper::toDto);
    }

    @Override
    public Page<ProjectPolygonDto> findAllByProjectId(Pageable pageable, UUID projectId) {
        return projectPolygonRepository.findAllByProjectId(pageable, projectId)
                .map(projectPolygonMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectPolygonDto update(UUID id, UpdateProjectPolygonDto updateProjectPolygonDto) {
        ProjectPolygon projectPolygon = projectPolygonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPolygon not found with id: " + id));
        projectPolygonMapper.update(projectPolygon, updateProjectPolygonDto);
        projectPolygon = projectPolygonRepository.save(projectPolygon);

        Map<String, Object> payload = objectMapper.convertValue(projectPolygon, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);

        return projectPolygonMapper.toDto(projectPolygon);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        projectPolygonRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }
}