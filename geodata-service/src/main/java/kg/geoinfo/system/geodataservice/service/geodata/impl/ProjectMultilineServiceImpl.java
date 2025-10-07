package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectMultilineMapper;
import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.repository.ProjectMultilineRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectMultilineService;
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
public class ProjectMultilineServiceImpl implements ProjectMultilineService {

    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectMultilineMapper projectMultilineMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProjectMultilineDto create(CreateProjectMultilineDto createProjectMultilineDto) {
        ProjectMultiline projectMultiline = projectMultilineMapper.toEntity(createProjectMultilineDto);
        projectMultiline = projectMultilineRepository.save(projectMultiline);

        Map<String, Object> payload = objectMapper.convertValue(projectMultiline, Map.class);
        payload.put("type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);

        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMultilineDto findById(UUID id) {
        return projectMultilineRepository.findById(id)
                .map(projectMultilineMapper::toDto)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectMultilineDto> findAll(Pageable pageable) {
        return projectMultilineRepository.findAll(pageable)
                .map(projectMultilineMapper::toDto);
    }

    @Override
    public Page<ProjectMultilineDto> findAllByProjectId(Pageable pageable, UUID projectId) {
        return projectMultilineRepository.findAllByProjectId(pageable, projectId)
                .map(projectMultilineMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectMultilineDto update(UUID id, UpdateProjectMultilineDto updateProjectMultilineDto) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        projectMultilineMapper.update(projectMultiline, updateProjectMultilineDto);
        projectMultiline = projectMultilineRepository.save(projectMultiline);

        Map<String, Object> payload = objectMapper.convertValue(projectMultiline, Map.class);
        payload.put("type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);

        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        projectMultilineRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id, "type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }
}