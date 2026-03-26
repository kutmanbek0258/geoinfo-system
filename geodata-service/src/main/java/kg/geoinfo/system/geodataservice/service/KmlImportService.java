package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import org.springframework.web.multipart.MultipartFile;

public interface KmlImportService {
    ProjectDto importKml(String currentUserEmail, MultipartFile file, String projectName);
}
