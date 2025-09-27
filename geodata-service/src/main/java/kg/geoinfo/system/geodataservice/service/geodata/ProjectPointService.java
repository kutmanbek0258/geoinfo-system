package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectPointService {
    ProjectPointDto create(CreateProjectPointDto createProjectPointDto);

    ProjectPointDto findById(UUID id);

    Page<ProjectPointDto> findAll(Pageable pageable);

    ProjectPointDto update(UUID id, UpdateProjectPointDto updateProjectPointDto);

    void delete(UUID id);
}
