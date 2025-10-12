package kg.geoinfo.system.geodataservice.controller.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPointService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/points")
@RequiredArgsConstructor
public class ProjectPointController {

    private final ProjectPointService projectPointService;

    @PostMapping
    @PreAuthorize("hasAuthority('GEO_FEATURE_CREATE')")
    public ResponseEntity<ProjectPointDto> create(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @RequestBody CreateProjectPointDto createProjectPointDto) {
        return new ResponseEntity<>(projectPointService.create(principal.getName(), createProjectPointDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<ProjectPointDto> findById(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(projectPointService.findById(principal.getName(), id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<Page<ProjectPointDto>> findAll(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectPointService.findAll(principal.getName(), pageable));
    }

    @GetMapping("/by-project-id/{projectId}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<Page<ProjectPointDto>> findAllByProjectId(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID projectId, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectPointService.findByProjectId(principal.getName(), pageable, projectId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_UPDATE')")
    public ResponseEntity<ProjectPointDto> update(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id, @RequestBody UpdateProjectPointDto updateProjectPointDto) {
        return ResponseEntity.ok(projectPointService.update(principal.getName(), id, updateProjectPointDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_DELETE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id) {
        projectPointService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
