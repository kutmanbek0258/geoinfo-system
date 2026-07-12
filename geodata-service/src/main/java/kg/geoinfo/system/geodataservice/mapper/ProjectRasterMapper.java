package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.ProjectRasterDto;
import kg.geoinfo.system.geodataservice.models.ProjectRaster;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProjectRasterMapper extends EntityMapper<ProjectRasterDto, ProjectRaster> {

    @Override
    @Mapping(source = "layer.id", target = "layerId")
    @Mapping(source = "folder.id", target = "folderId")
    ProjectRasterDto toDto(ProjectRaster entity);

    @Override
    @Mapping(target = "layer", ignore = true)
    @Mapping(target = "folder", ignore = true)
    ProjectRaster toEntity(ProjectRasterDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "layer", ignore = true)
    @Mapping(target = "folder", ignore = true)
    void update(@MappingTarget ProjectRaster entity, ProjectRasterDto dto);
}
