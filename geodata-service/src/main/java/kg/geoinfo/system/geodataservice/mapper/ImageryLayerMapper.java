package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.ImageryLayerDto;
import kg.geoinfo.system.geodataservice.models.ImageryLayer;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ImageryLayerMapper extends EntityMapper<ImageryLayerDto, ImageryLayer> {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget ImageryLayer entity, ImageryLayerDto dto);
}