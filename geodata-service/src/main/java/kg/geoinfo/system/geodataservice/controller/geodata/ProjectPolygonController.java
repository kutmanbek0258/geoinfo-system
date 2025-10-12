package kg.geoinfo.system.geodataservice.controller.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPolygonDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPolygonDto;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPolygonService;
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
@RequestMapping("/api/geodata/polygons")
@RequiredArgsConstructor
public class ProjectPolygonController {

    private final ProjectPolygonService projectPolygonService;

    @PostMapping
    @PreAuthorize("hasAuthority('GEO_FEATURE_CREATE')")
    public ResponseEntity<ProjectPolygonDto> create(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @RequestBody CreateProjectPolygonDto createProjectPolygonDto) {
        return new ResponseEntity<>(projectPolygonService.create(principal.getName(), createProjectPolygonDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<ProjectPolygonDto> findById(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(projectPolygonService.findById(principal.getName(), id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<Page<ProjectPolygonDto>> findAll(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectPolygonService.findAll(principal.getName(), pageable));
    }

    @GetMapping("/by-project-id/{projectId}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_READ')")
    public ResponseEntity<Page<ProjectPolygonDto>> findAllByProjectId(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID projectId, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectPolygonService.findAllByProjectId(principal.getName(), pageable, projectId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_UPDATE')")
    public ResponseEntity<ProjectPolygonDto> update(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id, @RequestBody UpdateProjectPolygonDto updateProjectPolygonDto) {
        return ResponseEntity.ok(projectPolygonService.update(principal.getName(), id, updateProjectPolygonDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_FEATURE_DELETE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID id) {
        projectPolygonService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
