package kg.geoinfo.system.geoabstraction.mapper;

import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import kg.geoinfo.system.geoabstraction.dto.TerrainLayerDto;
import kg.geoinfo.system.geoabstraction.models.GeoAbstractJob;
import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GeoAbstractMapper {
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    GeoAbstractJobDto toDto(GeoAbstractJob entity);

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "createdAt", source = "createdDate")
    TerrainLayerDto toDto(TerrainLayer entity);
}
