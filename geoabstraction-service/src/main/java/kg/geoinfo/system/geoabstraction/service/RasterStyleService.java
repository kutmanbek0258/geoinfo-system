package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.geoabstraction.dto.RasterStyleDto;
import kg.geoinfo.system.geoabstraction.mapper.RasterStyleMapper;
import kg.geoinfo.system.geoabstraction.models.RasterStyle;
import kg.geoinfo.system.geoabstraction.repository.RasterStyleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RasterStyleService {
    private final RasterStyleRepository repository;
    private final RasterStyleMapper mapper;

    public RasterStyleDto save(RasterStyleDto dto) {
        log.info("Saving new raster style: {}", dto.getTitle());
        RasterStyle entity = mapper.toEntity(dto);
        entity.setSystem(false); // New styles created via API are never system styles
        entity = repository.save(entity);
        return mapper.toDto(entity);
    }

    public RasterStyleDto update(RasterStyleDto dto, UUID id) {
        log.info("Updating raster style: {}", id);
        RasterStyle entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found: " + id));
        
        if (entity.isSystem()) {
            // Allow updating system styles titles/configs, but don't let them change names/is_system flag
            dto.setName(entity.getName());
            dto.setSystem(true);
        }
        
        mapper.update(entity, dto);
        entity = repository.save(entity);
        return mapper.toDto(entity);
    }

    public void deleteById(UUID id) {
        log.info("Deleting raster style: {}", id);
        RasterStyle entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found: " + id));
        
        if (entity.isSystem()) {
            throw new RuntimeException("System styles cannot be deleted");
        }
        
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public RasterStyleDto findById(UUID id) {
        RasterStyle entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found: " + id));
        return mapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public RasterStyleDto findByName(String name) {
        RasterStyle entity = repository.findByName(name)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found with name: " + name));
        return mapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public Page<RasterStyleDto> findByCondition(RasterStyleDto dto, Pageable pageable) {
        Page<RasterStyle> entityPage = repository.findByCondition(dto.getName(), dto.getTitle(), pageable);
        List<RasterStyle> entities = entityPage.getContent();
        return new PageImpl<>(mapper.toDto(entities), pageable, entityPage.getTotalElements());
    }
}
