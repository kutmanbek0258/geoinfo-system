package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.LayerDto;
import java.util.List;
import java.util.UUID;

public interface LayerService {
    LayerDto create(LayerDto dto);
    LayerDto update(UUID id, LayerDto dto);
    void delete(UUID id);
    LayerDto getById(UUID id);
    List<LayerDto> getByProject(UUID projectId);
}
