package kg.geoinfo.system.geoabstraction.mapper;

import kg.geoinfo.system.geoabstraction.dto.ImageryLayerDto;
import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ImageryLayerMapper extends EntityMapper<ImageryLayerDto, ImageryLayer> {
    @Mapping(target = "style", ignore = true)
    void update(@MappingTarget ImageryLayer entity, ImageryLayerDto dto);
}
