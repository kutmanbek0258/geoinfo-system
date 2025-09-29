package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.kafka.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectPointMapper;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import kg.geoinfo.system.geodataservice.repository.ProjectPointRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPointService;
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
public class ProjectPointServiceImpl implements ProjectPointService {

    private final ProjectPointRepository projectPointRepository;
    private final ProjectPointMapper projectPointMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProjectPointDto create(CreateProjectPointDto createProjectPointDto) {
        ProjectPoint projectPoint = projectPointMapper.toEntity(createProjectPointDto);
        projectPoint = projectPointRepository.save(projectPoint);

        Map<String, Object> payload = objectMapper.convertValue(projectPoint, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);

        return projectPointMapper.toDto(projectPoint);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPointDto findById(UUID id) {
        return projectPointRepository.findById(id)
                .map(projectPointMapper::toDto)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectPointDto> findAll(Pageable pageable) {
        return projectPointRepository.findAll(pageable)
                .map(projectPointMapper::toDto);
    }

    @Override
    public Page<ProjectPointDto> findByProjectId(Pageable pageable, UUID projectId) {
        return projectPointRepository.findAllByProjectId(pageable, projectId)
                .map(projectPointMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectPointDto update(UUID id, UpdateProjectPointDto updateProjectPointDto) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        projectPointMapper.update(projectPoint, updateProjectPointDto);
        projectPoint = projectPointRepository.save(projectPoint);

        Map<String, Object> payload = objectMapper.convertValue(projectPoint, Map.class);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);

        return projectPointMapper.toDto(projectPoint);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        projectPointRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id);
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }
}