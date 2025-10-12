package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPolygonDto;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectPolygonService {
    ProjectPolygonDto create(String currentUserEmail, CreateProjectPolygonDto createProjectPolygonDto);

    ProjectPolygonDto findById(String currentUserEmail, UUID id);

    Page<ProjectPolygonDto> findAll(String currentUserEmail, Pageable pageable);

    Page<ProjectPolygonDto> findAllByProjectId(String currentUserEmail, Pageable pageable, UUID projectId);

    ProjectPolygonDto update(String currentUserEmail, UUID id, UpdateProjectPolygonDto updateProjectPolygonDto);

    void delete(String currentUserEmail, UUID id);
}
