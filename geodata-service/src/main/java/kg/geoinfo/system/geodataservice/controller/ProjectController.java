package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import kg.geoinfo.system.geodataservice.dto.ShareProjectDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RequestMapping("/api/geodata/project")
@RestController
@Slf4j
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GEO_PROJECT_CREATE')")
    public ResponseEntity<ProjectDto> save(@RequestBody @Validated ProjectDto projectDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.save(projectDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_PROJECT_READ')")
    public ResponseEntity<ProjectDto> findById(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable("id") UUID id) {
        ProjectDto project = projectService.findById(principal.getName(), id);
        return ResponseEntity.ok(project);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_PROJECT_DELETE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable("id") UUID id) {
        projectService.deleteById(principal.getName(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/page-query")
    @PreAuthorize("hasAuthority('GEO_PROJECT_READ')")
    public ResponseEntity<Page<ProjectDto>> pageQuery(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, ProjectDto projectDto, @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProjectDto> projectPage = projectService.findByCondition(principal.getName(), projectDto, pageable);
        return ResponseEntity.ok(projectPage);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GEO_PROJECT_UPDATE')")
    public ResponseEntity<ProjectDto> update(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @RequestBody @Validated ProjectDto projectDto, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(projectService.update(principal.getName(), projectDto, id));
    }

    @PostMapping("/{projectId}/share")
    @PreAuthorize("hasAuthority('GEO_PROJECT_SHARE')")
    public ResponseEntity<Void> shareProject(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable("projectId") UUID projectId, @RequestBody @Validated ShareProjectDto shareDto) {
        projectService.shareProject(principal.getName(), projectId, shareDto);
        return ResponseEntity.ok().build();
    }
}