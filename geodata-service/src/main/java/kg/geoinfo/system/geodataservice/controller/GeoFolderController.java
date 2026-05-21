package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.GeoFolderDto;
import kg.geoinfo.system.geodataservice.service.GeoFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/folders")
@RequiredArgsConstructor
public class GeoFolderController {

    private final GeoFolderService folderService;

    @PostMapping
    public ResponseEntity<GeoFolderDto> create(@RequestBody GeoFolderDto dto) {
        return ResponseEntity.ok(folderService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GeoFolderDto> update(@PathVariable UUID id, @RequestBody GeoFolderDto dto) {
        return ResponseEntity.ok(folderService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        folderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GeoFolderDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(folderService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<GeoFolderDto>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(folderService.getByProject(projectId));
    }
}
