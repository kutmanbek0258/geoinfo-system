package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.RasterStyleDto;
import kg.geoinfo.system.geodataservice.models.RasterStyle;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface RasterStyleMapper extends EntityMapper<RasterStyleDto, RasterStyle> {

    @Override
    RasterStyleDto toDto(RasterStyle entity);

    @Override
    RasterStyle toEntity(RasterStyleDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget RasterStyle entity, RasterStyleDto dto);
}
