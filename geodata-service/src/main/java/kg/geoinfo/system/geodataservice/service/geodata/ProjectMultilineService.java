package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;

import java.util.List;
import java.util.UUID;

public interface ProjectMultilineService {
    ProjectMultilineDto create(CreateProjectMultilineDto createProjectMultilineDto);

    ProjectMultilineDto findById(UUID id);

    List<ProjectMultilineDto> findAll();

    ProjectMultilineDto update(UUID id, UpdateProjectMultilineDto updateProjectMultilineDto);

    void delete(UUID id);
}
