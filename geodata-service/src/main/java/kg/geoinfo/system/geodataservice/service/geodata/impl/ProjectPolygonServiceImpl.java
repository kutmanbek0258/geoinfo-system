package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.client.DocumentDto;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectPolygonMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPolygon;
import kg.geoinfo.system.geodataservice.repository.ProjectPolygonRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.service.client.DocumentServiceClient;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPolygonService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
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
public class ProjectPolygonServiceImpl implements ProjectPolygonService {

    private final ProjectPolygonRepository projectPolygonRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPolygonMapper projectPolygonMapper;
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
    public ProjectPolygonDto create(String currentUserEmail, CreateProjectPolygonDto createProjectPolygonDto) {
        checkProjectAccess(currentUserEmail, createProjectPolygonDto.getProjectId());
        ProjectPolygon projectPolygon = projectPolygonMapper.toEntity(createProjectPolygonDto);
        projectPolygon = projectPolygonRepository.save(projectPolygon);

        Map<String, Object> payload = objectMapper.convertValue(projectPolygon, Map.class);
        payload.put("type", "polygon");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);

        return projectPolygonMapper.toDto(projectPolygon);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPolygonDto findById(String currentUserEmail, UUID id) {
        ProjectPolygon projectPolygon = projectPolygonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPolygon not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPolygon.getProject().getId());
        return projectPolygonMapper.toDto(projectPolygon);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectPolygonDto> findAll(String currentUserEmail, Pageable pageable) {
        return Page.empty(pageable);
    }

    @Override
    public Page<ProjectPolygonDto> findAllByProjectId(String currentUserEmail, Pageable pageable, UUID projectId) {
        checkProjectAccess(currentUserEmail, projectId);
        return projectPolygonRepository.findAllByProjectId(pageable, projectId)
                .map(projectPolygonMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectPolygonDto update(String currentUserEmail, UUID id, UpdateProjectPolygonDto updateProjectPolygonDto) {
        ProjectPolygon projectPolygon = projectPolygonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPolygon not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPolygon.getProject().getId());
        projectPolygonMapper.update(projectPolygon, updateProjectPolygonDto);
        projectPolygon = projectPolygonRepository.save(projectPolygon);

        Map<String, Object> payload = objectMapper.convertValue(projectPolygonMapper.toDto(projectPolygon), Map.class);
        payload.put("type", "polygon");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);

        return projectPolygonMapper.toDto(projectPolygon);
    }

    @Override
    @Transactional
    public void delete(String currentUserEmail, UUID id) {
        ProjectPolygon projectPolygon = projectPolygonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPolygon not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPolygon.getProject().getId());
        projectPolygonRepository.deleteById(id);
        Map<String, Object> payload = Map.of("id", id, "type", "polygon", "createdBy", projectPolygon.getCreatedBy());
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.DELETED);
    }

    @Override
    public ProjectPolygonDto uploadMainImage(String currentUserEmail, UUID id, MultipartFile file) {
        ProjectPolygon projectPolygon = projectPolygonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPolygon.getProject().getId());

        DocumentDto documentDto = documentServiceClient.uploadDocument(file, id, "Main image for " + projectPolygon.getName(), "main-image");
        projectPolygon.setImageUrl("/api/documents/public/image/" + documentDto.getId());
        projectPolygon = projectPolygonRepository.save(projectPolygon);

        return projectPolygonMapper.toDto(projectPolygon);
    }
}