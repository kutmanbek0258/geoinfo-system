package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.TerrainLayerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TerrainLayerService {
    Page<TerrainLayerDto> getLayers(Pageable pageable);
    TerrainLayerDto getById(UUID id);
    TerrainLayerDto create(TerrainLayerDto dto);
    TerrainLayerDto update(UUID id, TerrainLayerDto dto);
    void delete(UUID id);
    String generateTerrainPresignedUrl(UUID layerId);
}
