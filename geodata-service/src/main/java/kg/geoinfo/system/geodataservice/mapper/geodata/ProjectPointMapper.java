package kg.geoinfo.system.geodataservice.mapper.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointSummaryDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProjectPointMapper {

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "folder.id", target = "folderId")
    ProjectPointDto toDto(ProjectPoint projectPoint);

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "folder.id", target = "folderId")
    ProjectPointSummaryDto toSummaryDto(ProjectPoint projectPoint);

    @Mapping(target = "project", source = "projectId", qualifiedByName = "mapProject")
    @Mapping(target = "folder", source = "folderId", qualifiedByName = "mapFolder")
    ProjectPoint toEntity(CreateProjectPointDto createProjectPointDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "folder", source = "folderId", qualifiedByName = "mapFolder")
    void update(@MappingTarget ProjectPoint projectPoint, UpdateProjectPointDto updateProjectPointDto);

    @Named("mapFolder")
    default GeoFolder mapFolder(UUID id) {
        if (id == null) {
            return null;
        }
        GeoFolder folder = new GeoFolder();
        folder.setId(id);
        return folder;
    }

    @Named("mapProject")
    default Project mapProject(UUID id) {
        if (id == null) {
            return null;
        }
        Project project = new Project();
        project.setId(id);
        return project;
    }
}
