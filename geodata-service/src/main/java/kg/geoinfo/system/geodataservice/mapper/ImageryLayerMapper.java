package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.ImageryLayerDto;
import kg.geoinfo.system.geodataservice.models.ImageryLayer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ImageryLayerMapper extends EntityMapper<ImageryLayerDto, ImageryLayer> {
}