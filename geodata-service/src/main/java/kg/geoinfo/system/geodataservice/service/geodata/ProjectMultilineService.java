package kg.geoinfo.system.geodataservice.service.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectMultilineService {
    ProjectMultilineDto create(String currentUserEmail, CreateProjectMultilineDto createProjectMultilineDto);

    ProjectMultilineDto findById(String currentUserEmail, UUID id);

    Page<ProjectMultilineDto> findAll(String currentUserEmail, Pageable pageable);

    Page<ProjectMultilineDto> findAllByProjectId(String currentUserEmail, Pageable pageable, UUID projectId);

    Page<ProjectMultilineSummaryDto> findSummaryByProjectId(String currentUserEmail, Pageable pageable, UUID projectId);

    ProjectMultilineDto update(String currentUserEmail, UUID id, UpdateProjectMultilineDto updateProjectMultilineDto);

    void delete(String currentUserEmail, UUID id);

    ProjectMultilineDto uploadMainImage(String currentUserEmail, UUID id, MultipartFile file);

    List<GeometryPartDto> getParts(String currentUserEmail, UUID id, double minX, double minY, double maxX, double maxY);

    void updateParts(String currentUserEmail, UUID id, UpdateGeometryPartsDto updateGeometryPartsDto);
}
