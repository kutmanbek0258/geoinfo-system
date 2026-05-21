package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.GeoFolderDto;
import kg.geoinfo.system.geodataservice.models.GeoFolder;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface GeoFolderMapper extends EntityMapper<GeoFolderDto, GeoFolder> {
    
    @Override
    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "parent.id", target = "parentId")
    GeoFolderDto toDto(GeoFolder entity);

    @Override
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "parent", ignore = true)
    GeoFolder toEntity(GeoFolderDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "parent", ignore = true)
    void update(@MappingTarget GeoFolder entity, GeoFolderDto dto);
}
