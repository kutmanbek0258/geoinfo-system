package kg.geoinfo.system.geodataservice.mapper.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProjectPointMapper {

    @Mapping(source = "project.id", target = "projectId")
    ProjectPointDto toDto(ProjectPoint projectPoint);

    @Mapping(target = "project", source = "projectId", qualifiedByName = "mapProject")
    ProjectPoint toEntity(CreateProjectPointDto createProjectPointDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget ProjectPoint projectPoint, UpdateProjectPointDto updateProjectPointDto);

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
