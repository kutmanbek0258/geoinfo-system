package kg.geoinfo.system.terrainservice.controller;

import kg.geoinfo.system.terrainservice.dto.TerrainJobDto;
import kg.geoinfo.system.terrainservice.dto.TerrainLayerDto;
import kg.geoinfo.system.terrainservice.service.TerrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/terrain")
@RequiredArgsConstructor
public class TerrainController {

    private final TerrainService terrainService;

    @PostMapping("/jobs")
    public ResponseEntity<TerrainJobDto> createJob(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(terrainService.createJob(name, file));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<TerrainJobDto> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(terrainService.getJob(id));
    }

    @GetMapping("/layers")
    public ResponseEntity<Page<TerrainLayerDto>> getAllLayers(@PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable){
        return ResponseEntity.ok(terrainService.getLayers(pageable));
    }

    @DeleteMapping("/layers/{id}")
    public ResponseEntity<Void> deleteLayer(@PathVariable UUID id) {
        terrainService.deleteLayer(id);
        return ResponseEntity.noContent().build();
    }
}
