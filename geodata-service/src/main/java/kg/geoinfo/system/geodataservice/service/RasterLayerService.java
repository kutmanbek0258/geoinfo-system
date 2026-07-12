package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.RasterLayerDto;
import java.util.List;
import java.util.UUID;

public interface RasterLayerService {
    RasterLayerDto create(RasterLayerDto dto);
    RasterLayerDto update(UUID id, RasterLayerDto dto);
    void delete(UUID id);
    RasterLayerDto getById(UUID id);
    List<RasterLayerDto> getAll();
}
