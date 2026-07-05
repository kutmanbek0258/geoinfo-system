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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/geo-abstraction")
@RequiredArgsConstructor
public class GeoAbstractionController {

    private final GeoAbstractionService geoAbstractionService;

    @PostMapping("/jobs")
    public ResponseEntity<GeoAbstractJobDto> createJob(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createJob(name, file, projectId));
    }

    @PostMapping("/sentinel/upload")
    public ResponseEntity<GeoAbstractJobDto> createSentinelJob(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam("channels") List<String> channels,
            @RequestParam(value = "indexType", required = false) String indexType,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createSentinelJob(name, file, channels, indexType, projectId));
    }

    @PostMapping("/landsat/upload")
    public ResponseEntity<GeoAbstractJobDto> createLandsatJob(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam("channels") List<String> channels,
            @RequestParam(value = "indexType", required = false) String indexType,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createLandsatJob(name, file, channels, indexType, projectId));
    }

    @PostMapping("/imagery-layer/upload")
    public ResponseEntity<GeoAbstractJobDto> uploadRawGeoTiff(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createRawGeoTiffJob(name, file, projectId));
    }

    @PostMapping("/terrain/upload")
    public ResponseEntity<GeoAbstractJobDto> uploadTerrain(
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createTerrainJob(name, file, projectId));
    }

    @GetMapping("/upload/presigned-url")
    public ResponseEntity<Map<String, String>> getPresignedUrl(@RequestParam("filename") String filename) {
        String result = geoAbstractionService.generateUploadUrl(filename);
        String[] parts = result.split("###");
        Map<String, String> response = new HashMap<>();
        response.put("url", parts[0]);
        response.put("objectKey", parts[1]);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/jobs/verify-upload")
    public ResponseEntity<GeoAbstractJobDto> verifyUpload(
            @RequestParam("name") String name,
            @RequestParam("objectKey") String objectKey,
            @RequestParam("fileSize") Long fileSize,
            @RequestParam("dataType") String dataType,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createJobVerify(name, objectKey, fileSize, dataType, projectId));
    }

    @PostMapping("/jobs/{id}/import")
    public ResponseEntity<GeoAbstractJobDto> startImport(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> params) {
        return ResponseEntity.ok(geoAbstractionService.startImport(id, params));
    }

    @PostMapping("/jobs/confirm")
    public ResponseEntity<GeoAbstractJobDto> confirmJob(
            @RequestParam("name") String name,
            @RequestParam("objectKey") String objectKey,
            @RequestParam("fileSize") Long fileSize,
            @RequestParam("taskType") String taskType,
            @RequestParam(value = "channels", required = false) List<String> channels,
            @RequestParam(value = "indexType", required = false) String indexType,
            @RequestParam(value = "projectId", required = false) UUID projectId) {
        return ResponseEntity.ok(geoAbstractionService.createJobConfirm(name, objectKey, fileSize, taskType, channels, indexType, projectId));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<GeoAbstractJobDto> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(geoAbstractionService.getJob(id));
    }

    @GetMapping("/jobs")
    public ResponseEntity<Page<GeoAbstractJobDto>> getAllJobs(
            @RequestParam(value = "projectId", required = false) UUID projectId,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable){
        return ResponseEntity.ok(geoAbstractionService.getJobs(pageable, projectId));
    }

    @GetMapping("/layers")
    public ResponseEntity<Page<TerrainLayerDto>> getAllLayers(
            @RequestParam(value = "projectId", required = false) UUID projectId,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable){
        return ResponseEntity.ok(geoAbstractionService.getLayers(pageable, projectId));
    }

    @GetMapping("/layers/{id}/presigned-url")
    public ResponseEntity<Map<String, String>> getTerrainPresignedUrl(@PathVariable UUID id) {
        String url = geoAbstractionService.generateTerrainPresignedUrl(id);
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/layers/{id}")
    public ResponseEntity<Void> deleteLayer(@PathVariable UUID id) {
        geoAbstractionService.deleteLayer(id);
        return ResponseEntity.noContent().build();
    }
}
