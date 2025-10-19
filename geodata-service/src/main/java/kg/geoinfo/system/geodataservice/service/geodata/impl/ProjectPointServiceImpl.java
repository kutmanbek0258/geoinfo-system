package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectPointMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import kg.geoinfo.system.geodataservice.repository.ProjectPointRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPointService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import kg.geoinfo.system.geodataservice.service.client.DocumentServiceClient;
import kg.geoinfo.system.geodataservice.dto.client.DocumentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectPointServiceImpl implements ProjectPointService {

    private final ProjectPointRepository projectPointRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPointMapper projectPointMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final DocumentServiceClient documentServiceClient;

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
    public ProjectPointDto create(String currentUserEmail, CreateProjectPointDto createProjectPointDto) {
        checkProjectAccess(currentUserEmail, createProjectPointDto.getProjectId());
        ProjectPoint projectPoint = projectPointMapper.toEntity(createProjectPointDto);
        projectPoint = projectPointRepository.save(projectPoint);

        Map<String, Object> payload = objectMapper.convertValue(projectPoint, Map.class);
        payload.put("type", "point");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);

        return projectPointMapper.toDto(projectPoint);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPointDto findById(String currentUserEmail, UUID id) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());
        return projectPointMapper.toDto(projectPoint);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectPointDto> findAll(String currentUserEmail, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Page<ProjectPointDto> findByProjectId(String currentUserEmail, Pageable pageable, UUID projectId) {
        checkProjectAccess(currentUserEmail, projectId);
        return projectPointRepository.findAllByProjectId(pageable, projectId)
                .map(projectPointMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectPointDto update(String currentUserEmail, UUID id, UpdateProjectPointDto updateProjectPointDto) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());
        projectPointMapper.update(projectPoint, updateProjectPointDto);
        projectPoint = projectPointRepository.save(projectPoint);

        Map<String, Object> payload = objectMapper.convertValue(projectPointMapper.toDto(projectPoint), Map.class);
        payload.put("type", "point");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);

        return projectPointMapper.toDto(projectPoint);
    }

    @Override
    @Transactional
    public void delete(String currentUserEmail, UUID id) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());
        projectPointRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id, "type", "point", "createdBy", projectPoint.getCreatedBy());
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }

    @Override
    @Transactional
    public ProjectPointDto uploadMainImage(String currentUserEmail, UUID id, MultipartFile file) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());

        DocumentDto documentDto = documentServiceClient.uploadDocument(file, id, "Main image for " + projectPoint.getName(), "main-image");
        projectPoint.setImageUrl("/api/documents/public/image/" + documentDto.getId());
        projectPoint = projectPointRepository.save(projectPoint);

        return projectPointMapper.toDto(projectPoint);
    }
}