package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.hierarchy.*;
import kg.geoinfo.system.geodataservice.models.*;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.service.ProjectHierarchyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectHierarchyServiceImpl implements ProjectHierarchyService {

    private final ProjectRepository projectRepository;
    private final LayerRepository layerRepository;
    private final GeoFolderRepository folderRepository;
    private final ProjectPointRepository projectPointRepository;
    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectPolygonRepository projectPolygonRepository;
    private final ProjectRasterRepository projectRasterRepository;

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
    @Transactional(readOnly = true)
    public ProjectHierarchyDto getProjectHierarchy(String currentUserEmail, UUID projectId) {
        log.info("Fetching project hierarchy for projectId: {} by user: {}", projectId, currentUserEmail);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));
        checkProjectAccess(currentUserEmail, projectId);

        List<Layer> layers = layerRepository.findAllByProjectId(projectId);
        List<GeoFolder> allFolders = folderRepository.findAllByLayerProjectId(projectId);

        // Fetch all objects for all layers in the project
        List<ProjectPoint> allPoints = new ArrayList<>();
        List<ProjectMultiline> allMultilines = new ArrayList<>();
        List<ProjectPolygon> allPolygons = new ArrayList<>();
        List<ProjectRaster> allRasters = new ArrayList<>();

        for (Layer layer : layers) {
            allPoints.addAll(projectPointRepository.findAllByLayerId(layer.getId()));
            allMultilines.addAll(projectMultilineRepository.findAllByLayerId(layer.getId()));
            allPolygons.addAll(projectPolygonRepository.findAllByLayerId(layer.getId()));
            allRasters.addAll(projectRasterRepository.findAllByLayerId(layer.getId()));
        }

        // Convert to DTOs
        List<HierarchyObjectDto> allObjects = new ArrayList<>();

        for (ProjectPoint p : allPoints) {
            allObjects.add(HierarchyObjectDto.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .description(p.getDescription())
                    .type("Point")
                    .status(p.getStatus())
                    .folderId(p.getFolder() != null ? p.getFolder().getId() : null)
                    .layerId(p.getLayer() != null ? p.getLayer().getId() : null)
                    .projectId(projectId)
                    .characteristics(p.getCharacteristics())
                    .build());
        }

        for (ProjectMultiline m : allMultilines) {
            allObjects.add(HierarchyObjectDto.builder()
                    .id(m.getId())
                    .name(m.getName())
                    .description(m.getDescription())
                    .type("MultiLineString")
                    .status(m.getStatus())
                    .folderId(m.getFolder() != null ? m.getFolder().getId() : null)
                    .layerId(m.getLayer() != null ? m.getLayer().getId() : null)
                    .projectId(projectId)
                    .characteristics(m.getCharacteristics())
                    .build());
        }

        for (ProjectPolygon poly : allPolygons) {
            allObjects.add(HierarchyObjectDto.builder()
                    .id(poly.getId())
                    .name(poly.getName())
                    .description(poly.getDescription())
                    .type("Polygon")
                    .status(poly.getStatus())
                    .folderId(poly.getFolder() != null ? poly.getFolder().getId() : null)
                    .layerId(poly.getLayer() != null ? poly.getLayer().getId() : null)
                    .projectId(projectId)
                    .characteristics(poly.getCharacteristics())
                    .build());
        }

        for (ProjectRaster r : allRasters) {
            allObjects.add(HierarchyObjectDto.builder()
                    .id(r.getId())
                    .name(r.getName())
                    .description(r.getDescription())
                    .type("Raster")
                    .status(r.getStatus())
                    .folderId(r.getFolder() != null ? r.getFolder().getId() : null)
                    .layerId(r.getLayer() != null ? r.getLayer().getId() : null)
                    .projectId(projectId)
                    .cogObjectKey(r.getCogObjectKey())
                    .crs(r.getCrs())
                    .colormapId(r.getColormapId())
                    .resampling(r.getResampling())
                    .characteristics(r.getCharacteristics())
                    .build());
        }

        // Map folders by ID
        Map<UUID, HierarchyFolderDto> folderMap = new HashMap<>();
        Map<UUID, List<HierarchyFolderDto>> layerRootFolders = new HashMap<>();

        for (GeoFolder f : allFolders) {
            HierarchyFolderDto folderDto = HierarchyFolderDto.builder()
                    .id(f.getId())
                    .name(f.getName())
                    .description(f.getDescription())
                    .layerId(f.getLayer().getId())
                    .parentId(f.getParent() != null ? f.getParent().getId() : null)
                    .projectId(projectId)
                    .characteristics(f.getCharacteristics())
                    .subfolders(new ArrayList<>())
                    .objects(new ArrayList<>())
                    .build();
            folderMap.put(f.getId(), folderDto);
        }

        // Establish folder hierarchy parent-child links
        for (GeoFolder f : allFolders) {
            HierarchyFolderDto folderDto = folderMap.get(f.getId());
            if (f.getParent() != null && folderMap.containsKey(f.getParent().getId())) {
                folderMap.get(f.getParent().getId()).getSubfolders().add(folderDto);
            } else {
                layerRootFolders.computeIfAbsent(f.getLayer().getId(), k -> new ArrayList<>()).add(folderDto);
            }
        }

        // Distribute features and rasters to their respective folders or root layers
        Map<UUID, List<HierarchyObjectDto>> layerRootObjects = new HashMap<>();
        
        for (HierarchyObjectDto obj : allObjects) {
            if (obj.getFolderId() != null && folderMap.containsKey(obj.getFolderId())) {
                // Feature belongs to a folder. Add to folder's objects list.
                folderMap.get(obj.getFolderId()).getObjects().add(obj);
            } else {
                // Feature has no folder. Place directly under its layer root.
                UUID resolvedLayerId = obj.getLayerId();
                if (resolvedLayerId == null) {
                    // Fallback to first VECTOR layer of the project
                    Optional<Layer> defaultVectorLayer = layers.stream()
                            .filter(l -> l.getType() == kg.geoinfo.system.geodataservice.models.enums.LayerType.VECTOR)
                            .findFirst();
                    if (defaultVectorLayer.isPresent()) {
                        resolvedLayerId = defaultVectorLayer.get().getId();
                    }
                }
                if (resolvedLayerId != null) {
                    layerRootObjects.computeIfAbsent(resolvedLayerId, k -> new ArrayList<>()).add(obj);
                }
            }
        }

        // Assemble HierarchyLayerDtos
        List<HierarchyLayerDto> layerDtos = new ArrayList<>();
        for (Layer layer : layers) {
            UUID layerId = layer.getId();
            List<HierarchyFolderDto> rootFolders = layerRootFolders.getOrDefault(layerId, Collections.emptyList());
            List<HierarchyObjectDto> rootObjects = layerRootObjects.getOrDefault(layerId, Collections.emptyList());

            layerDtos.add(HierarchyLayerDto.builder()
                    .id(layerId)
                    .name(layer.getName())
                    .type(layer.getType())
                    .projectId(projectId)
                    .folders(rootFolders)
                    .objects(rootObjects)
                    .build());
        }

        return ProjectHierarchyDto.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .layers(layerDtos)
                .build();
    }
}
