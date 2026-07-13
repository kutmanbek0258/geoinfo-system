package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.LayerDto;
import kg.geoinfo.system.geodataservice.mapper.LayerMapper;
import kg.geoinfo.system.geodataservice.models.Layer;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.service.LayerService;
import kg.geoinfo.system.geodataservice.service.FileStoreService;
import kg.geoinfo.system.geodataservice.config.MinioProperties;
import kg.geoinfo.system.geodataservice.models.ProjectRaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LayerServiceImpl implements LayerService {

    private final LayerRepository layerRepository;
    private final ProjectRepository projectRepository;
    private final LayerMapper layerMapper;

    private final ProjectPointRepository pointRepository;
    private final ProjectMultilineRepository multilineRepository;
    private final ProjectPolygonRepository polygonRepository;
    private final ProjectRasterRepository rasterRepository;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public LayerDto create(LayerDto dto) {
        log.info("Creating layer: {}", dto.getName());
        Layer layer = layerMapper.toEntity(dto);

        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + dto.getProjectId()));
        layer.setProject(project);

        return layerMapper.toDto(layerRepository.save(layer));
    }

    @Override
    @Transactional
    public LayerDto update(UUID id, LayerDto dto) {
        log.info("Updating layer: {}", id);
        Layer layer = layerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Layer not found: " + id));

        layerMapper.update(layer, dto);

        return layerMapper.toDto(layerRepository.save(layer));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting layer: {}", id);
        Layer layer = layerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Layer not found: " + id));

        // 1. Delete all points, lines, polygons under this layer
        pointRepository.deleteAllByLayerId(id);
        multilineRepository.deleteAllByLayerId(id);
        polygonRepository.deleteAllByLayerId(id);

        // 2. Delete all raster files from MinIO under this layer
        List<ProjectRaster> rasters = rasterRepository.findAllByLayerId(id);
        for (ProjectRaster raster : rasters) {
            if (raster.getCogObjectKey() != null && !raster.getCogObjectKey().isEmpty()) {
                try {
                    fileStoreService.deleteFile(minioProperties.getBucket(), raster.getCogObjectKey());
                } catch (Exception e) {
                    log.error("Failed to delete raster file from MinIO: key={}, error={}", raster.getCogObjectKey(), e.getMessage());
                }
            }
        }

        // 3. Delete the layer (database cascade deletes folders and project_rasters)
        layerRepository.delete(layer);
    }

    @Override
    public LayerDto getById(UUID id) {
        return layerRepository.findById(id)
                .map(layerMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Layer not found: " + id));
    }

    @Override
    public List<LayerDto> getByProject(UUID projectId) {
        return layerMapper.toDto(layerRepository.findAllByProjectId(projectId));
    }
}
