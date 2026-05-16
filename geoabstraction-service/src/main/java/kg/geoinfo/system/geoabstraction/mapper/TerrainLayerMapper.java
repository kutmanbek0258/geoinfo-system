package kg.geoinfo.system.geoabstraction.mapper;

import kg.geoinfo.system.geoabstraction.dto.TerrainLayerDto;
import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TerrainLayerMapper extends EntityMapper<TerrainLayerDto, TerrainLayer>{
}
