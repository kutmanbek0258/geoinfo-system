package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectMultilineService {
    ProjectMultilineDto create(CreateProjectMultilineDto createProjectMultilineDto);

    ProjectMultilineDto findById(UUID id);

    Page<ProjectMultilineDto> findAll(Pageable pageable);

    ProjectMultilineDto update(UUID id, UpdateProjectMultilineDto updateProjectMultilineDto);

    void delete(UUID id);
}
