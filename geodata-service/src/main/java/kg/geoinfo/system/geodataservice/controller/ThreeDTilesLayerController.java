package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.ThreeDTilesLayerDto;
import kg.geoinfo.system.geodataservice.service.ThreeDTilesLayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/3dtiles-layers")
@RequiredArgsConstructor
public class ThreeDTilesLayerController {

    private final ThreeDTilesLayerService threeDTilesLayerService;

    @PostMapping
    public ResponseEntity<ThreeDTilesLayerDto> create(@RequestBody ThreeDTilesLayerDto dto) {
        return ResponseEntity.ok(threeDTilesLayerService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThreeDTilesLayerDto> update(@PathVariable UUID id, @RequestBody ThreeDTilesLayerDto dto) {
        return ResponseEntity.ok(threeDTilesLayerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        threeDTilesLayerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThreeDTilesLayerDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(threeDTilesLayerService.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ThreeDTilesLayerDto>> getLayers(
            @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(threeDTilesLayerService.getLayers(pageable));
    }
}
