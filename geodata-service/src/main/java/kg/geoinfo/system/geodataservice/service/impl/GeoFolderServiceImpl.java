package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.GeoFolderDto;
import kg.geoinfo.system.geodataservice.mapper.GeoFolderMapper;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.service.GeoFolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoFolderServiceImpl implements GeoFolderService {

    private final GeoFolderRepository folderRepository;
    private final ProjectRepository projectRepository;
    private final GeoFolderMapper folderMapper;
    
    private final ProjectPointRepository pointRepository;
    private final ProjectMultilineRepository multilineRepository;
    private final ProjectPolygonRepository polygonRepository;

    @Override
    @Transactional
    public GeoFolderDto create(GeoFolderDto dto) {
        log.info("Creating folder: {}", dto.getName());
        GeoFolder folder = folderMapper.toEntity(dto);
        
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + dto.getProjectId()));
        folder.setProject(project);
        
        if (dto.getParentId() != null) {
            GeoFolder parent = folderRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent folder not found: " + dto.getParentId()));
            folder.setParent(parent);
        }
        
        return folderMapper.toDto(folderRepository.save(folder));
    }

    @Override
    @Transactional
    public GeoFolderDto update(UUID id, GeoFolderDto dto) {
        log.info("Updating folder: {}", id);
        GeoFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found: " + id));
        
        folderMapper.update(folder, dto);
        
        if (dto.getParentId() != null) {
            GeoFolder parent = folderRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent folder not found: " + dto.getParentId()));
            folder.setParent(parent);
        } else {
             folder.setParent(null);
        }
        
        return folderMapper.toDto(folderRepository.save(folder));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting folder: {}", id);
        GeoFolder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found: " + id));
        
        // 1. Move subfolders to root
        List<GeoFolder> subfolders = folderRepository.findAll().stream()
                .filter(f -> f.getParent() != null && f.getParent().getId().equals(id))
                .toList();
        subfolders.forEach(sf -> sf.setParent(null));
        folderRepository.saveAll(subfolders);
        
        // 2. Move points to root
        pointRepository.findAllByFolderId(id).forEach(p -> {
            p.setFolder(null);
            pointRepository.save(p);
        });
        
        // 3. Move multilines to root
        multilineRepository.findAllByFolderId(id).forEach(m -> {
            m.setFolder(null);
            multilineRepository.save(m);
        });
        
        // 4. Move polygons to root
        polygonRepository.findAllByFolderId(id).forEach(p -> {
            p.setFolder(null);
            polygonRepository.save(p);
        });
        
        folderRepository.delete(folder);
    }

    @Override
    public GeoFolderDto getById(UUID id) {
        return folderRepository.findById(id)
                .map(folderMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Folder not found: " + id));
    }

    @Override
    public List<GeoFolderDto> getByProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        return folderMapper.toDto(folderRepository.findAllByProject(project));
    }
}
