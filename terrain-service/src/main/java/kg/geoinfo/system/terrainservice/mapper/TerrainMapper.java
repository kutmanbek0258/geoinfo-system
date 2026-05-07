package kg.geoinfo.system.terrainservice.mapper;

import kg.geoinfo.system.terrainservice.dto.TerrainJobDto;
import kg.geoinfo.system.terrainservice.dto.TerrainLayerDto;
import kg.geoinfo.system.terrainservice.models.TerrainJob;
import kg.geoinfo.system.terrainservice.models.TerrainLayer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TerrainMapper {
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    TerrainJobDto toDto(TerrainJob entity);

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "createdAt", source = "createdDate")
    TerrainLayerDto toDto(TerrainLayer entity);
}
