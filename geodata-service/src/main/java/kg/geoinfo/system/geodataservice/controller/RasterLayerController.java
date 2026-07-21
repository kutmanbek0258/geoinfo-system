package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.RasterLayerDto;
import kg.geoinfo.system.geodataservice.service.RasterLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/raster-layers")
@RequiredArgsConstructor
public class RasterLayerController {

    private final RasterLayerService layerService;

    @PostMapping
    public ResponseEntity<RasterLayerDto> create(@RequestBody RasterLayerDto dto) {
        return ResponseEntity.ok(layerService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RasterLayerDto> update(@PathVariable UUID id, @RequestBody RasterLayerDto dto) {
        return ResponseEntity.ok(layerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        layerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RasterLayerDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(layerService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<RasterLayerDto>> getAll() {
        return ResponseEntity.ok(layerService.getAll());
    }

    @GetMapping("/{id}/presigned-url")
    public ResponseEntity<java.util.Map<String, String>> getPresignedUrl(@PathVariable UUID id) {
        String url = layerService.generatePresignedUrl(id);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("url", url);
        return ResponseEntity.ok(response);
    }
}

