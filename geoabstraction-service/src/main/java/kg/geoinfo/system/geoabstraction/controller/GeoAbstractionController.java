package kg.geoinfo.system.geoabstraction.controller;

import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import kg.geoinfo.system.geoabstraction.dto.TerrainLayerDto;
import kg.geoinfo.system.geoabstraction.service.GeoAbstractionService;
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
@RequestMapping("/api/geo-abstraction")
@RequiredArgsConstructor
public class GeoAbstractionController {

    private final GeoAbstractionService geoAbstractionService;

    @PostMapping("/jobs")
    public ResponseEntity<GeoAbstractJobDto> createJob(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(geoAbstractionService.createJob(name, file));
    }

    @PostMapping("/sentinel/upload")
    public ResponseEntity<GeoAbstractJobDto> createSentinelJob(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam("channels") List<String> channels) {
        return ResponseEntity.ok(geoAbstractionService.createSentinelJob(name, file, channels));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<GeoAbstractJobDto> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(geoAbstractionService.getJob(id));
    }

    @GetMapping("/jobs")
    public ResponseEntity<Page<GeoAbstractJobDto>> getAllJobs(@PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable){
        return ResponseEntity.ok(geoAbstractionService.getJobs(pageable));
    }

    @GetMapping("/layers")
    public ResponseEntity<Page<TerrainLayerDto>> getAllLayers(@PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable){
        return ResponseEntity.ok(geoAbstractionService.getLayers(pageable));
    }

    @DeleteMapping("/layers/{id}")
    public ResponseEntity<Void> deleteLayer(@PathVariable UUID id) {
        geoAbstractionService.deleteLayer(id);
        return ResponseEntity.noContent().build();
    }
}
