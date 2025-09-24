package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPolygonDto;

import java.util.List;
import java.util.UUID;

public interface ProjectPolygonService {
    ProjectPolygonDto create(CreateProjectPolygonDto createProjectPolygonDto);

    ProjectPolygonDto findById(UUID id);

    List<ProjectPolygonDto> findAll();

    ProjectPolygonDto update(UUID id, UpdateProjectPolygonDto updateProjectPolygonDto);

    void delete(UUID id);
}
