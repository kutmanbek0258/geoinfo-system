package kg.geoinfo.system.userservice.mapper;

import kg.geoinfo.system.userservice.dto.ProjectDto;
import kg.geoinfo.system.userservice.models.Project;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectMapper extends EntityMapper<ProjectDto, Project> {
}