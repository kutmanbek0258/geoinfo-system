package kg.geoinfo.system.terrainservice.mapper;

import kg.geoinfo.system.terrainservice.dto.TerrainLayerDto;
import kg.geoinfo.system.terrainservice.models.TerrainLayer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TerrainLayerMapper extends EntityMapper<TerrainLayerDto, TerrainLayer>{
}
