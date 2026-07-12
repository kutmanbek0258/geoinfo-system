package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.geodata.*;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectPointMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import kg.geoinfo.system.geodataservice.repository.ProjectPointRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPointService;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import kg.geoinfo.system.geodataservice.repository.LayerRepository;
import kg.geoinfo.system.geodataservice.repository.GeoFolderRepository;
import kg.geoinfo.system.geodataservice.service.client.DocumentServiceClient;
import kg.geoinfo.system.geodataservice.models.Layer;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import kg.geoinfo.system.geodataservice.dto.client.DocumentDto;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectPointServiceImpl implements ProjectPointService {

    private final ProjectPointRepository projectPointRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPointMapper projectPointMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final DocumentServiceClient documentServiceClient;
    private final LayerRepository layerRepository;
    private final GeoFolderRepository folderRepository;

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
        log.info("Creating point for user: {}", currentUserEmail);
        checkProjectAccess(currentUserEmail, createProjectPointDto.getProjectId());

        ProjectPoint projectPoint = projectPointMapper.toEntity(createProjectPointDto);
        projectPoint.setGeom(GeometryUtils.ensureMultiPoint3D(projectPoint.getGeom()));

        Layer layer = null;
        if (createProjectPointDto.getFolderId() != null) {
            GeoFolder folder = folderRepository.findById(createProjectPointDto.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
            layer = folder.getLayer();
            projectPoint.setFolder(folder);
        } else {
            final UUID projectId = createProjectPointDto.getProjectId();
            layer = layerRepository.findAllByProjectId(projectId).stream()
                    .filter(l -> l.getType() == LayerType.VECTOR)
                    .findFirst()
                    .orElseGet(() -> {
                        Project project = projectRepository.findById(projectId).orElseThrow();
                        Layer newLayer = Layer.builder()
                                .project(project)
                                .name("Векторные данные")
                                .type(LayerType.VECTOR)
                                .build();
                        return layerRepository.save(newLayer);
                    });
        }
        projectPoint.setLayer(layer);

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
    @Transactional(readOnly = true)
    public Page<ProjectPointSummaryDto> findSummaryByProjectId(String currentUserEmail, Pageable pageable, UUID projectId) {
        checkProjectAccess(currentUserEmail, projectId);
        return projectPointRepository.findAllByProjectId(pageable, projectId)
                .map(projectPointMapper::toSummaryDto);
    }

    @Override
    @Transactional
    public ProjectPointDto update(String currentUserEmail, UUID id, UpdateProjectPointDto updateProjectPointDto) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());
        projectPointMapper.update(projectPoint, updateProjectPointDto);
        projectPoint.setGeom(GeometryUtils.ensureMultiPoint3D(projectPoint.getGeom()));
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

    @Override
    @Transactional(readOnly = true)
    public List<GeometryPartDto> getParts(String currentUserEmail, UUID id, double minX, double minY, double maxX, double maxY) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());

        return projectPointRepository.findPartsInBBox(id, minX, minY, maxX, maxY).stream()
                .map(p -> new GeometryPartDto(p.getSubId(), p.getGeojson()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateParts(String currentUserEmail, UUID id, UpdateGeometryPartsDto updateGeometryPartsDto) {
        ProjectPoint projectPoint = projectPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectPoint not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectPoint.getProject().getId());

        updateGeometryPartsDto.getParts().forEach(part -> {
            try {
                Geometry geom = objectMapper.readValue(part.getGeojson(), Geometry.class);
                projectPointRepository.updatePart(id, part.getSubId(), GeometryUtils.toWkt3D(geom));
            } catch (JsonProcessingException e) {
                log.error("Error parsing GeoJSON part for point {}: {}", id, e.getMessage());
                throw new RuntimeException("Invalid GeoJSON part", e);
            }
        });

        // Fetch updated object to send event
        ProjectPoint updated = projectPointRepository.findById(id).orElseThrow();
        Map<String, Object> payload = objectMapper.convertValue(projectPointMapper.toDto(updated), Map.class);
        payload.put("type", "point");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);
    }
}