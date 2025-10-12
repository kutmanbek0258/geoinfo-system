
package kg.geoinfo.system.geodataservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.dto.ShareProjectDto;
import kg.geoinfo.system.geodataservice.mapper.ProjectMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectAccess;
import kg.geoinfo.system.geodataservice.repository.ProjectAccessRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    private final ProjectRepository projectRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final ProjectMapper projectMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public ProjectDto save(ProjectDto projectDto) {
        Project entity = projectMapper.toEntity(projectDto);
        entity = projectRepository.save(entity);
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        payload.put("type", "project");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);
        return projectMapper.toDto(entity);
    }

    public void deleteById(String currentUserEmail, UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        if (!project.getCreatedBy().equals(currentUserEmail)) {
            throw new AccessDeniedException("You are not the owner of this project");
        }
        projectRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id, "type", "project");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }

    public ProjectDto findById(String currentUserEmail, UUID id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        boolean hasAccess = project.getCreatedBy().equals(currentUserEmail) ||
                            project.getAccesses().stream()
                                   .anyMatch(pa -> pa.getId().getUserEmail().equals(currentUserEmail));
        if (!hasAccess) {
            throw new AccessDeniedException("You don't have access to this project");
        }
        return projectMapper.toDto(project);
    }

    public Page<ProjectDto> findByCondition(String currentUserEmail, ProjectDto projectDto, Pageable pageable) {
        Page<Project> entityPage = projectRepository.findAllExtended(currentUserEmail, pageable);
        List<Project> entities = entityPage.getContent();
        return new PageImpl<>(projectMapper.toDto(entities), pageable, entityPage.getTotalElements());
    }

    public ProjectDto update(String currentUserEmail, ProjectDto projectDto, UUID id) {
        Project entity = projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        if (!entity.getCreatedBy().equals(currentUserEmail)) {
            throw new AccessDeniedException("You are not the owner of this project");
        }
        projectMapper.update(entity, projectDto);
        entity = projectRepository.save(entity);
        Map<String, Object> payload = objectMapper.convertValue(entity, Map.class);
        payload.put("type", "project");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);
        return projectMapper.toDto(entity);
    }

    public void shareProject(String ownerEmail, UUID projectId, ShareProjectDto shareDto) {
        Project project = projectRepository.findById(projectId)
                                           .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getCreatedBy().equals(ownerEmail)) {
            throw new AccessDeniedException("Only the project owner can share the project.");
        }

        ProjectAccess.ProjectAccessId accessId = new ProjectAccess.ProjectAccessId(projectId, shareDto.getEmail());
        ProjectAccess projectAccess = ProjectAccess.builder()
                                                   .id(accessId)
                                                   .project(project)
                                                   .permissionLevel(shareDto.getPermissionLevel())
                                                   .build();
        projectAccessRepository.save(projectAccess);
    }
}

