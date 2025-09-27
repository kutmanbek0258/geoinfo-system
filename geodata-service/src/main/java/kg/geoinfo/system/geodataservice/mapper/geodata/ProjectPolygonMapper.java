package kg.geoinfo.system.geodataservice.mapper.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPolygon;
import org.mapstruct.*;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProjectPolygonMapper {

    @Mapping(source = "project.id", target = "projectId")
    ProjectPolygonDto toDto(ProjectPolygon projectPolygon);

    @Mapping(target = "project", source = "projectId", qualifiedByName = "mapProject")
    ProjectPolygon toEntity(CreateProjectPolygonDto createProjectPolygonDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget ProjectPolygon projectPolygon, UpdateProjectPolygonDto updateProjectPolygonDto);

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
