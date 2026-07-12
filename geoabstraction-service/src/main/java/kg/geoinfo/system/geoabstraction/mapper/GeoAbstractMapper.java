package kg.geoinfo.system.geoabstraction.mapper;

import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import kg.geoinfo.system.geoabstraction.models.GeoAbstractJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GeoAbstractMapper {
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "updatedAt", source = "lastModifiedDate")
    GeoAbstractJobDto toDto(GeoAbstractJob entity);
}
