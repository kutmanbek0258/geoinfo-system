package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.ProjectRasterDto;
import kg.geoinfo.system.geodataservice.mapper.ProjectRasterMapper;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import kg.geoinfo.system.geodataservice.models.Layer;
import kg.geoinfo.system.geodataservice.models.ProjectRaster;
import kg.geoinfo.system.geodataservice.repository.GeoFolderRepository;
import kg.geoinfo.system.geodataservice.repository.LayerRepository;
import kg.geoinfo.system.geodataservice.repository.ProjectRasterRepository;
import kg.geoinfo.system.geodataservice.service.ProjectRasterService;
import kg.geoinfo.system.geodataservice.service.FileStoreService;
import kg.geoinfo.system.geodataservice.config.MinioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectRasterServiceImpl implements ProjectRasterService {

    private final ProjectRasterRepository rasterRepository;
    private final LayerRepository layerRepository;
    private final GeoFolderRepository folderRepository;
    private final ProjectRasterMapper rasterMapper;
    private final FileStoreService fileStoreService;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public ProjectRasterDto create(ProjectRasterDto dto) {
        log.info("Creating project raster: {}", dto.getName());
        ProjectRaster raster = rasterMapper.toEntity(dto);

        Layer layer = layerRepository.findById(dto.getLayerId())
                .orElseThrow(() -> new RuntimeException("Layer not found: " + dto.getLayerId()));
        raster.setLayer(layer);

        if (dto.getFolderId() != null) {
            GeoFolder folder = folderRepository.findById(dto.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found: " + dto.getFolderId()));
            raster.setFolder(folder);
        }

        return rasterMapper.toDto(rasterRepository.save(raster));
    }

    @Override
    @Transactional
    public ProjectRasterDto update(UUID id, ProjectRasterDto dto) {
        log.info("Updating project raster: {}", id);
        ProjectRaster raster = rasterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectRaster not found: " + id));

        rasterMapper.update(raster, dto);

        if (dto.getFolderId() != null) {
            GeoFolder folder = folderRepository.findById(dto.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found: " + dto.getFolderId()));
            raster.setFolder(folder);
        } else {
            raster.setFolder(null);
        }

        return rasterMapper.toDto(rasterRepository.save(raster));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting project raster: {}", id);
        ProjectRaster raster = rasterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectRaster not found: " + id));
        
        if (raster.getCogObjectKey() != null && !raster.getCogObjectKey().isEmpty()) {
            try {
                fileStoreService.deleteFile(minioProperties.getBucket(), raster.getCogObjectKey());
            } catch (Exception e) {
                log.error("Failed to delete raster file from MinIO: key={}, error={}", raster.getCogObjectKey(), e.getMessage());
            }
        }
        
        rasterRepository.delete(raster);
    }

    @Override
    public ProjectRasterDto getById(UUID id) {
        return rasterRepository.findById(id)
                .map(rasterMapper::toDto)
                .orElseThrow(() -> new RuntimeException("ProjectRaster not found: " + id));
    }

    @Override
    public List<ProjectRasterDto> getByLayer(UUID layerId) {
        return rasterMapper.toDto(rasterRepository.findAllByLayerId(layerId));
    }

    @Override
    public List<ProjectRasterDto> getByFolder(UUID folderId) {
        return rasterMapper.toDto(rasterRepository.findAllByFolderId(folderId));
    }
}
