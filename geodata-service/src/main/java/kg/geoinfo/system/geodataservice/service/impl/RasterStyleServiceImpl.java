package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.RasterStyleDto;
import kg.geoinfo.system.geodataservice.mapper.RasterStyleMapper;
import kg.geoinfo.system.geodataservice.models.RasterStyle;
import kg.geoinfo.system.geodataservice.repository.RasterStyleRepository;
import kg.geoinfo.system.geodataservice.service.RasterStyleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RasterStyleServiceImpl implements RasterStyleService {

    private final RasterStyleRepository repository;
    private final RasterStyleMapper mapper;

    @Override
    @Transactional
    public RasterStyleDto save(RasterStyleDto dto) {
        log.info("Saving raster style: {}", dto.getName());
        RasterStyle style = mapper.toEntity(dto);
        return mapper.toDto(repository.save(style));
    }

    @Override
    @Transactional
    public RasterStyleDto update(RasterStyleDto dto, UUID id) {
        log.info("Updating raster style: {}", id);
        RasterStyle style = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found: " + id));
        mapper.update(style, dto);
        return mapper.toDto(repository.save(style));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        log.info("Deleting raster style: {}", id);
        RasterStyle style = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found: " + id));
        if (style.isSystem()) {
            throw new RuntimeException("Cannot delete system styles");
        }
        repository.delete(style);
    }

    @Override
    public RasterStyleDto findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new RuntimeException("RasterStyle not found: " + id));
    }

    @Override
    public Page<RasterStyleDto> findByCondition(RasterStyleDto dto, Pageable pageable) {
        Page<RasterStyle> styles = repository.findByCondition(dto.getName(), dto.getTitle(), pageable);
        List<RasterStyleDto> dtoList = styles.getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, styles.getTotalElements());
    }

    @Override
    public List<RasterStyleDto> getAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
}
