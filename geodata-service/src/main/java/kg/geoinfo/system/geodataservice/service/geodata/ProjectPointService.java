package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectPointService {
    ProjectPointDto create(String currentUserEmail, CreateProjectPointDto createProjectPointDto);

    ProjectPointDto findById(String currentUserEmail, UUID id);

    Page<ProjectPointDto> findAll(String currentUserEmail, Pageable pageable);

    Page<ProjectPointDto> findByProjectId(String currentUserEmail, Pageable pageable, UUID projectId);

    ProjectPointDto update(String currentUserEmail, UUID id, UpdateProjectPointDto updateProjectPointDto);

    void delete(String currentUserEmail, UUID id);

    ProjectPointDto uploadMainImage(String currentUserEmail, UUID id, MultipartFile file);
}
