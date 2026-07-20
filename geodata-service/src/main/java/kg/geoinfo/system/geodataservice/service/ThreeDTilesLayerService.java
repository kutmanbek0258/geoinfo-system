package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.ThreeDTilesLayerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ThreeDTilesLayerService {
    Page<ThreeDTilesLayerDto> getLayers(Pageable pageable);
    ThreeDTilesLayerDto getById(UUID id);
    ThreeDTilesLayerDto create(ThreeDTilesLayerDto dto);
    ThreeDTilesLayerDto update(UUID id, ThreeDTilesLayerDto dto);
    void delete(UUID id);
}
