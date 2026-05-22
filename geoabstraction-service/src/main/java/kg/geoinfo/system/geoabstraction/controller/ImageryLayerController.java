package kg.geoinfo.system.geoabstraction.controller;

import kg.geoinfo.system.geoabstraction.dto.ImageryLayerDto;
import kg.geoinfo.system.geoabstraction.service.ImageryLayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/geo-abstraction/imagery-layer")
@RestController
@Slf4j
public class ImageryLayerController {
    private final ImageryLayerService imageryLayerService;

    public ImageryLayerController(ImageryLayerService imageryLayerService) {
        this.imageryLayerService = imageryLayerService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_CREATE')")
    public ResponseEntity<ImageryLayerDto> save(@RequestBody @Validated ImageryLayerDto imageryLayerDto) {
        return ResponseEntity.ok(imageryLayerService.save(imageryLayerDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_READ')")
    public ResponseEntity<ImageryLayerDto> findById(@PathVariable("id") UUID id) {
        ImageryLayerDto imageryLayer = imageryLayerService.findById(id);
        return ResponseEntity.ok(imageryLayer);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        imageryLayerService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/page-query")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_READ')")
    public ResponseEntity<Page<ImageryLayerDto>> pageQuery(ImageryLayerDto imageryLayerDto, @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ImageryLayerDto> imageryLayerPage = imageryLayerService.findByCondition(imageryLayerDto, pageable);
        return ResponseEntity.ok(imageryLayerPage);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_UPDATE')")
    public ResponseEntity<ImageryLayerDto> update(@PathVariable("id") UUID id, @RequestBody ImageryLayerDto imageryLayerDto) {
        return ResponseEntity.ok(imageryLayerService.update(imageryLayerDto, id));
    }

    @GetMapping("/styles")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_READ')")
    public ResponseEntity<java.util.List<String>> getStyles() {
        return ResponseEntity.ok(imageryLayerService.getStyles());
    }
}
