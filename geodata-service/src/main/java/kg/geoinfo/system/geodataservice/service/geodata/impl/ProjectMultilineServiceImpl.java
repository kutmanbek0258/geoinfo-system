package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectMultilineMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.repository.ProjectMultilineRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectMultilineService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectMultilineServiceImpl implements ProjectMultilineService {

    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMultilineMapper projectMultilineMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    private void checkProjectAccess(String currentUserEmail, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));

        boolean hasAccess = project.getCreatedBy().equals(currentUserEmail) ||
                            project.getAccesses().stream()
                                   .anyMatch(pa -> pa.getId().getUserEmail().equals(currentUserEmail));

        if (!hasAccess) {
            throw new AccessDeniedException("User does not have access to the project with id: " + projectId);
        }
    }

    @Override
    @Transactional
    public ProjectMultilineDto create(String currentUserEmail, CreateProjectMultilineDto createProjectMultilineDto) {
        checkProjectAccess(currentUserEmail, createProjectMultilineDto.getProjectId());
        ProjectMultiline projectMultiline = projectMultilineMapper.toEntity(createProjectMultilineDto);
        projectMultiline = projectMultilineRepository.save(projectMultiline);

        Map<String, Object> payload = objectMapper.convertValue(projectMultiline, Map.class);
        payload.put("type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);

        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMultilineDto findById(String currentUserEmail, UUID id) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());
        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectMultilineDto> findAll(String currentUserEmail, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Page<ProjectMultilineDto> findAllByProjectId(String currentUserEmail, Pageable pageable, UUID projectId) {
        checkProjectAccess(currentUserEmail, projectId);
        return projectMultilineRepository.findAllByProjectId(pageable, projectId)
                .map(projectMultilineMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectMultilineDto update(String currentUserEmail, UUID id, UpdateProjectMultilineDto updateProjectMultilineDto) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());
        projectMultilineMapper.update(projectMultiline, updateProjectMultilineDto);
        projectMultiline = projectMultilineRepository.save(projectMultiline);

        Map<String, Object> payload = objectMapper.convertValue(projectMultiline, Map.class);
        payload.put("type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);

        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional
    public void delete(String currentUserEmail, UUID id) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());
        projectMultilineRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id, "type", "multiline", "createdBy", projectMultiline.getCreatedBy());
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }
}