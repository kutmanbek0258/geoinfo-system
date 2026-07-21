package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.RasterLayerDto;
import kg.geoinfo.system.geodataservice.mapper.RasterLayerMapper;
import kg.geoinfo.system.geodataservice.models.RasterLayer;
import kg.geoinfo.system.geodataservice.repository.RasterLayerRepository;
import kg.geoinfo.system.geodataservice.service.RasterLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import kg.geoinfo.system.geodataservice.service.FileStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RasterLayerServiceImpl implements RasterLayerService {

    private final RasterLayerRepository repository;
    private final RasterLayerMapper mapper;
    private final FileStoreService fileStoreService;

    @Override
    @Transactional
    public RasterLayerDto create(RasterLayerDto dto) {
        log.info("Creating raster layer: {}", dto.getName());
        RasterLayer layer = mapper.toEntity(dto);
        return mapper.toDto(repository.save(layer));
    }

    @Override
    @Transactional
    public RasterLayerDto update(UUID id, RasterLayerDto dto) {
        log.info("Updating raster layer: {}", id);
        RasterLayer layer = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterLayer not found: " + id));
        mapper.update(layer, dto);
        return mapper.toDto(repository.save(layer));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting raster layer: {}", id);
        RasterLayer layer = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterLayer not found: " + id));
        repository.delete(layer);
    }

    @Override
    public RasterLayerDto getById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new RuntimeException("RasterLayer not found: " + id));
    }

    @Override
    public List<RasterLayerDto> getAll() {
        return mapper.toDto(repository.findAll());
    }

    @Override
    public String generatePresignedUrl(UUID id) {
        RasterLayer layer = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("RasterLayer not found: " + id));
        if (layer.getCogObjectKey() == null) {
            throw new RuntimeException("RasterLayer has no cogObjectKey: " + id);
        }
        return fileStoreService.generateDownloadUrl(layer.getCogObjectKey());
    }
}

