package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;

import java.util.List;
import java.util.UUID;

public interface ProjectPointService {
    ProjectPointDto create(CreateProjectPointDto createProjectPointDto);

    ProjectPointDto findById(UUID id);

    List<ProjectPointDto> findAll();

    ProjectPointDto update(UUID id, UpdateProjectPointDto updateProjectPointDto);

    void delete(UUID id);
}
