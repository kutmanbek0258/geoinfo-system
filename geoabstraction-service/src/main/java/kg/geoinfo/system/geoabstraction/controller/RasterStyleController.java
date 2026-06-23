package kg.geoinfo.system.geoabstraction.controller;

import kg.geoinfo.system.geoabstraction.dto.RasterStyleDto;
import kg.geoinfo.system.geoabstraction.service.RasterStyleService;
import lombok.RequiredArgsConstructor;
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

@RequestMapping("/api/geo-abstraction/raster-style")
@RestController
@Slf4j
@RequiredArgsConstructor
public class RasterStyleController {
    private final RasterStyleService service;

    @PostMapping
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_CREATE')")
    public ResponseEntity<RasterStyleDto> save(@RequestBody @Validated RasterStyleDto dto) {
        return ResponseEntity.ok(service.save(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_READ')")
    public ResponseEntity<RasterStyleDto> findById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/page-query")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_READ')")
    public ResponseEntity<Page<RasterStyleDto>> pageQuery(
            RasterStyleDto dto,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.findByCondition(dto, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IMAGERY_LAYER_UPDATE')")
    public ResponseEntity<RasterStyleDto> update(@PathVariable("id") UUID id, @RequestBody RasterStyleDto dto) {
        return ResponseEntity.ok(service.update(dto, id));
    }
}
