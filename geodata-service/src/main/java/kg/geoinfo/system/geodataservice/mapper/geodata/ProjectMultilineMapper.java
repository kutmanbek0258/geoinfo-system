package kg.geoinfo.system.geodataservice.mapper.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProjectMultilineMapper {

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "folder.id", target = "folderId")
    ProjectMultilineDto toDto(ProjectMultiline projectMultiline);

    @Mapping(target = "project", source = "projectId", qualifiedByName = "mapProject")
    @Mapping(target = "folder", source = "folderId", qualifiedByName = "mapFolder")
    ProjectMultiline toEntity(CreateProjectMultilineDto createProjectMultilineDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "folder", source = "folderId", qualifiedByName = "mapFolder")
    void update(@MappingTarget ProjectMultiline projectMultiline, UpdateProjectMultilineDto updateProjectMultilineDto);

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
