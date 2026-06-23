package kg.geoinfo.system.geoabstraction.mapper;

import kg.geoinfo.system.geoabstraction.dto.RasterStyleDto;
import kg.geoinfo.system.geoabstraction.models.RasterStyle;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RasterStyleMapper extends EntityMapper<RasterStyleDto, RasterStyle> {
    void update(@MappingTarget RasterStyle entity, RasterStyleDto dto);
}
