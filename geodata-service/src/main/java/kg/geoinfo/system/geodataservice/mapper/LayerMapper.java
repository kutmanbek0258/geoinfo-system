package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.LayerDto;
import kg.geoinfo.system.geodataservice.models.Layer;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface LayerMapper extends EntityMapper<LayerDto, Layer> {

    @Override
    @Mapping(source = "project.id", target = "projectId")
    LayerDto toDto(Layer entity);

    @Override
    @Mapping(target = "project", ignore = true)
    Layer toEntity(LayerDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "project", ignore = true)
    void update(@MappingTarget Layer entity, LayerDto dto);
}
