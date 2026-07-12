package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.ProjectRasterDto;
import java.util.List;
import java.util.UUID;

public interface ProjectRasterService {
    ProjectRasterDto create(ProjectRasterDto dto);
    ProjectRasterDto update(UUID id, ProjectRasterDto dto);
    void delete(UUID id);
    ProjectRasterDto getById(UUID id);
    List<ProjectRasterDto> getByLayer(UUID layerId);
    List<ProjectRasterDto> getByFolder(UUID folderId);
}
