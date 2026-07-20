package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.ThreeDTilesLayerDto;
import kg.geoinfo.system.geodataservice.models.ThreeDTilesLayer;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ThreeDTilesLayerMapper extends EntityMapper<ThreeDTilesLayerDto, ThreeDTilesLayer> {

    @Override
    ThreeDTilesLayerDto toDto(ThreeDTilesLayer entity);

    @Override
    ThreeDTilesLayer toEntity(ThreeDTilesLayerDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget ThreeDTilesLayer entity, ThreeDTilesLayerDto dto);
}
