package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.ProjectRasterDto;
import kg.geoinfo.system.geodataservice.service.ProjectRasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/project-rasters")
@RequiredArgsConstructor
public class ProjectRasterController {

    private final ProjectRasterService rasterService;

    @PostMapping
    public ResponseEntity<ProjectRasterDto> create(@RequestBody ProjectRasterDto dto) {
        return ResponseEntity.ok(rasterService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectRasterDto> update(@PathVariable UUID id, @RequestBody ProjectRasterDto dto) {
        return ResponseEntity.ok(rasterService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        rasterService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectRasterDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(rasterService.getById(id));
    }

    @GetMapping("/layer/{layerId}")
    public ResponseEntity<List<ProjectRasterDto>> getByLayer(@PathVariable UUID layerId) {
        return ResponseEntity.ok(rasterService.getByLayer(layerId));
    }

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<ProjectRasterDto>> getByFolder(@PathVariable UUID folderId) {
        return ResponseEntity.ok(rasterService.getByFolder(folderId));
    }
}
