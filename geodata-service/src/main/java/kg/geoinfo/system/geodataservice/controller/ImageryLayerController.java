package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.ImageryLayerDto;
import kg.geoinfo.system.geodataservice.service.ImageryLayerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RequestMapping("/api/imagery-layer")
@RestController
@Slf4j
public class ImageryLayerController {
    private final ImageryLayerService imageryLayerService;

    public ImageryLayerController(ImageryLayerService imageryLayerService) {
        this.imageryLayerService = imageryLayerService;
    }

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody @Validated ImageryLayerDto imageryLayerDto) {
        imageryLayerService.save(imageryLayerDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageryLayerDto> findById(@PathVariable("id") UUID id) {
        ImageryLayerDto imageryLayer = imageryLayerService.findById(id);
        return ResponseEntity.ok(imageryLayer);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        Optional.ofNullable(imageryLayerService.findById(id)).orElseThrow(() -> {
            log.error("Unable to delete non-existent data！");
            return new ResourceNotFoundException("Unable to delete non-existent data！");
        });
        imageryLayerService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/page-query")
    public ResponseEntity<Page<ImageryLayerDto>> pageQuery(ImageryLayerDto imageryLayerDto, @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ImageryLayerDto> imageryLayerPage = imageryLayerService.findByCondition(imageryLayerDto, pageable);
        return ResponseEntity.ok(imageryLayerPage);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@RequestBody @Validated ImageryLayerDto imageryLayerDto, @PathVariable("id") UUID id) {
        imageryLayerService.update(imageryLayerDto, id);
        return ResponseEntity.ok().build();
    }
}