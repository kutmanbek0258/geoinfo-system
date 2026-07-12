package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.RasterLayerDto;
import kg.geoinfo.system.geodataservice.models.RasterLayer;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface RasterLayerMapper extends EntityMapper<RasterLayerDto, RasterLayer> {

    @Override
    RasterLayerDto toDto(RasterLayer entity);

    @Override
    RasterLayer toEntity(RasterLayerDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget RasterLayer entity, RasterLayerDto dto);
}
