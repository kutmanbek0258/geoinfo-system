package kg.geoinfo.system.geodataservice.controller.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectMultilineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/multilines")
@RequiredArgsConstructor
public class ProjectMultilineController {

    private final ProjectMultilineService projectMultilineService;

    @PostMapping
    @PreAuthorize("hasAuthority('GEO_FEATURE_CREATE')")
    public ResponseEntity<ProjectMultilineDto> create(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @RequestBody CreateProjectMultilineDto createProjectMultilineDto) {
        return new ResponseEntity<>(projectMultilineService.create(principal.getName(), createProjectMultilineDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<ProjectMultilineDto> findById(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(projectMultilineService.findById(principal.getName(), id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<Page<ProjectMultilineDto>> findAll(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectMultilineService.findAll(principal.getName(), pageable));
    }

    @GetMapping("/by-project-id/{projectId}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<Page<ProjectMultilineDto>> findAllByProjectId(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID projectId, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectMultilineService.findAllByProjectId(principal.getName(), pageable, projectId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_UPDATE')")
    public ResponseEntity<ProjectMultilineDto> update(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id, @RequestBody UpdateProjectMultilineDto updateProjectMultilineDto) {
        return ResponseEntity.ok(projectMultilineService.update(principal.getName(), id, updateProjectMultilineDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_DELETE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id) {
        projectMultilineService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/upload-main-image")
    @PreAuthorize("hasAuthority('GEO_FEATURE_UPDATE')")
    public ResponseEntity<ProjectMultilineDto> uploadMainImage(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(projectMultilineService.uploadMainImage(principal.getName(), id, file));
    }
}
