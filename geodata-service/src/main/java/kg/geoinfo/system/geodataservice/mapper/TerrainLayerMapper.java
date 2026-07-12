package kg.geoinfo.system.geodataservice.mapper;

import kg.geoinfo.system.geodataservice.dto.TerrainLayerDto;
import kg.geoinfo.system.geodataservice.models.TerrainLayer;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TerrainLayerMapper extends EntityMapper<TerrainLayerDto, TerrainLayer> {

    @Override
    TerrainLayerDto toDto(TerrainLayer entity);

    @Override
    TerrainLayer toEntity(TerrainLayerDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(@MappingTarget TerrainLayer entity, TerrainLayerDto dto);
}
