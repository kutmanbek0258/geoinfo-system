package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.LayerDto;
import kg.geoinfo.system.geodataservice.service.LayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/layers")
@RequiredArgsConstructor
public class LayerController {

    private final LayerService layerService;

    @PostMapping
    public ResponseEntity<LayerDto> create(@RequestBody LayerDto dto) {
        return ResponseEntity.ok(layerService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LayerDto> update(@PathVariable UUID id, @RequestBody LayerDto dto) {
        return ResponseEntity.ok(layerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        layerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LayerDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(layerService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<LayerDto>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(layerService.getByProject(projectId));
    }
}
