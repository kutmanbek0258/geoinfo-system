package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.hierarchy.ProjectHierarchyDto;

import java.util.UUID;

public interface ProjectHierarchyService {
    ProjectHierarchyDto getProjectHierarchy(String currentUserEmail, UUID projectId);
}
