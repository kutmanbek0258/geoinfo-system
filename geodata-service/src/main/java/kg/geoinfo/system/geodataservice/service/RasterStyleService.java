package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.dto.RasterStyleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RasterStyleService {
    RasterStyleDto save(RasterStyleDto dto);
    RasterStyleDto update(RasterStyleDto dto, UUID id);
    void deleteById(UUID id);
    RasterStyleDto findById(UUID id);
    Page<RasterStyleDto> findByCondition(RasterStyleDto dto, Pageable pageable);
    List<RasterStyleDto> getAll();
}
