package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.TerrainLayerDto;
import kg.geoinfo.system.geodataservice.service.TerrainLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/terrain-layers")
@RequiredArgsConstructor
public class TerrainLayerController {

    private final TerrainLayerService terrainLayerService;

    @PostMapping
    public ResponseEntity<TerrainLayerDto> create(@RequestBody TerrainLayerDto dto) {
        return ResponseEntity.ok(terrainLayerService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TerrainLayerDto> update(@PathVariable UUID id, @RequestBody TerrainLayerDto dto) {
        return ResponseEntity.ok(terrainLayerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        terrainLayerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TerrainLayerDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(terrainLayerService.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<TerrainLayerDto>> getLayers(
            @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(terrainLayerService.getLayers(pageable));
    }

    @GetMapping("/{id}/presigned-url")
    public ResponseEntity<Map<String, String>> getTerrainPresignedUrl(@PathVariable UUID id) {
        String url = terrainLayerService.generateTerrainPresignedUrl(id);
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return ResponseEntity.ok(response);
    }
}
