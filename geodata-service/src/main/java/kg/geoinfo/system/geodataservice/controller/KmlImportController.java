package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.service.KmlImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/import/kml")
@RequiredArgsConstructor
public class KmlImportController {

    private final KmlImportService kmlImportService;

    @PostMapping
    @PreAuthorize("hasAuthority('GEO_PROJECT_CREATE')")
    public ResponseEntity<ProjectDto> importKml(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectName", required = false) String projectName) {
        return ResponseEntity.ok(kmlImportService.importKml(principal.getName(), file, projectName));
    }

    @PostMapping("/{projectId}")
    @PreAuthorize("hasAuthority('GEO_PROJECT_CREATE')")
    public ResponseEntity<ProjectDto> importKmlToProject(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal,
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(kmlImportService.importKmlToProject(principal.getName(), projectId, file));
    }
}
