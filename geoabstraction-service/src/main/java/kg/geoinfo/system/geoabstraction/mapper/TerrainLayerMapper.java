package kg.geoinfo.system.geoabstraction.mapper;

import kg.geoinfo.system.geoabstraction.dto.TerrainLayerDto;
import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TerrainLayerMapper extends EntityMapper<TerrainLayerDto, TerrainLayer>{
    @Override
    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "cogUrl", ignore = true)
    TerrainLayerDto toDto(TerrainLayer entity);

    @Override
    @Mapping(target = "job", ignore = true)
    TerrainLayer toEntity(TerrainLayerDto dto);
}
