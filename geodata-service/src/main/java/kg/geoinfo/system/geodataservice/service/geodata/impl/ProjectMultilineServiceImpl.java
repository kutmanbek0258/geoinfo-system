package kg.geoinfo.system.geodataservice.service.geodata.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.geodata.*;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectMultilineMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.repository.ProjectMultilineRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectMultilineService;
import kg.geoinfo.system.geodataservice.repository.LayerRepository;
import kg.geoinfo.system.geodataservice.repository.GeoFolderRepository;
import kg.geoinfo.system.geodataservice.models.Layer;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import kg.geoinfo.system.geodataservice.service.client.DocumentServiceClient;
import kg.geoinfo.system.geodataservice.dto.client.DocumentDto;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiLineString;
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
public class ProjectMultilineServiceImpl implements ProjectMultilineService {

    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMultilineMapper projectMultilineMapper;
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
    public ProjectMultilineDto create(String currentUserEmail, CreateProjectMultilineDto createProjectMultilineDto) {
        checkProjectAccess(currentUserEmail, createProjectMultilineDto.getProjectId());
        ProjectMultiline projectMultiline = projectMultilineMapper.toEntity(createProjectMultilineDto);
        projectMultiline.setGeom(GeometryUtils.ensureMultiLineString3D(projectMultiline.getGeom()));

        Layer layer = null;
        if (createProjectMultilineDto.getFolderId() != null) {
            GeoFolder folder = folderRepository.findById(createProjectMultilineDto.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
            layer = folder.getLayer();
            projectMultiline.setFolder(folder);
        } else {
            final UUID projectId = createProjectMultilineDto.getProjectId();
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
        projectMultiline.setLayer(layer);

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
    @Transactional(readOnly = true)
    public Page<ProjectMultilineSummaryDto> findSummaryByProjectId(String currentUserEmail, Pageable pageable, UUID projectId) {
        checkProjectAccess(currentUserEmail, projectId);
        return projectMultilineRepository.findAllByProjectId(pageable, projectId)
                .map(projectMultilineMapper::toSummaryDto);
    }

    @Override
    @Transactional
    public ProjectMultilineDto update(String currentUserEmail, UUID id, UpdateProjectMultilineDto updateProjectMultilineDto) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());
        projectMultilineMapper.update(projectMultiline, updateProjectMultilineDto);
        projectMultiline.setGeom(GeometryUtils.ensureMultiLineString3D(projectMultiline.getGeom()));
        projectMultiline = projectMultilineRepository.save(projectMultiline);

        Map<String, Object> payload = objectMapper.convertValue(projectMultilineMapper.toDto(projectMultiline), Map.class);
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

    @Override
    @Transactional
    public ProjectMultilineDto uploadMainImage(String currentUserEmail, UUID id, MultipartFile file) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());

        DocumentDto documentDto = documentServiceClient.uploadDocument(file, id, "Main image for " + projectMultiline.getName(), "main-image");
        projectMultiline.setImageUrl("/api/documents/public/image/" + documentDto.getId());
        projectMultiline = projectMultilineRepository.save(projectMultiline);

        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeometryPartDto> getParts(String currentUserEmail, UUID id, double minX, double minY, double maxX, double maxY) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());

        return projectMultilineRepository.findPartsInBBox(id, minX, minY, maxX, maxY).stream()
                .map(p -> new GeometryPartDto(p.getSubId(), p.getGeojson()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateParts(String currentUserEmail, UUID id, UpdateGeometryPartsDto updateGeometryPartsDto) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        checkProjectAccess(currentUserEmail, projectMultiline.getProject().getId());

        updateGeometryPartsDto.getParts().forEach(part -> {
            try {
                Geometry geom = objectMapper.readValue(part.getGeojson(), Geometry.class);
                projectMultilineRepository.updatePart(id, part.getSubId(), GeometryUtils.toWkt3D(geom));
            } catch (JsonProcessingException e) {
                log.error("Error parsing GeoJSON part for multiline {}: {}", id, e.getMessage());
                throw new RuntimeException("Invalid GeoJSON part", e);
            }
        });

        // Fetch updated object to send event
        ProjectMultiline updated = projectMultilineRepository.findById(id).orElseThrow();
        Map<String, Object> payload = objectMapper.convertValue(projectMultilineMapper.toDto(updated), Map.class);
        payload.put("type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.UPDATED);
    }
}