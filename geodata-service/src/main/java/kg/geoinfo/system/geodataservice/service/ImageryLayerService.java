package kg.geoinfo.system.geodataservice.service;

import cn.hutool.core.bean.BeanUtil;
import jakarta.transaction.Transactional;
import kg.geoinfo.system.geodataservice.dto.ImageryLayerDto;
import kg.geoinfo.system.geodataservice.mapper.ImageryLayerMapper;
import kg.geoinfo.system.geodataservice.models.ImageryLayer;
import kg.geoinfo.system.geodataservice.repository.ImageryLayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ImageryLayerService {
    private final ImageryLayerRepository repository;
    private final ImageryLayerMapper imageryLayerMapper;

    public ImageryLayerService(ImageryLayerRepository repository, ImageryLayerMapper imageryLayerMapper) {
        this.repository = repository;
        this.imageryLayerMapper = imageryLayerMapper;
    }

    public ImageryLayerDto save(ImageryLayerDto imageryLayerDto) {
        ImageryLayer entity = imageryLayerMapper.toEntity(imageryLayerDto);
        return imageryLayerMapper.toDto(repository.save(entity));
    }

    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    public ImageryLayerDto findById(UUID id) {
        return imageryLayerMapper.toDto(repository.findById(id).orElseThrow());
    }

    public Page<ImageryLayerDto> findByCondition(ImageryLayerDto imageryLayerDto, Pageable pageable) {
        Page<ImageryLayer> entityPage = repository.findAll(pageable);
        List<ImageryLayer> entities = entityPage.getContent();
        return new PageImpl<>(imageryLayerMapper.toDto(entities), pageable, entityPage.getTotalElements());
    }

    public ImageryLayerDto update(ImageryLayerDto imageryLayerDto, UUID id) {
        ImageryLayerDto data = findById(id);
        ImageryLayer entity = imageryLayerMapper.toEntity(imageryLayerDto);
        BeanUtil.copyProperties(entity, data, "id");
        return save(data);
    }
}