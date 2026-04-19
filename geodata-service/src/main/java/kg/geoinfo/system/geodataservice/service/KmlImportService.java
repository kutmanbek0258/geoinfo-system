package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface KmlImportService {

    ProjectDto importKml(String currentUserEmail, MultipartFile file, String projectName);

    ProjectDto importKmlToProject(String currentUserEmail, UUID projectId, MultipartFile file);
}
