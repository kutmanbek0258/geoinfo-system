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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/multilines")
@RequiredArgsConstructor
public class ProjectMultilineController {

    private final ProjectMultilineService projectMultilineService;

    @PostMapping
    public ResponseEntity<ProjectMultilineDto> create(@RequestBody CreateProjectMultilineDto createProjectMultilineDto) {
        return new ResponseEntity<>(projectMultilineService.create(createProjectMultilineDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectMultilineDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectMultilineService.findById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProjectMultilineDto>> findAll(@PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectMultilineService.findAll(pageable));
    }

    @GetMapping("/by-project-id/{projectId}")
    public ResponseEntity<Page<ProjectMultilineDto>> findAllByProjectId(@PathVariable UUID projectId, @PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectMultilineService.findAllByProjectId(pageable, projectId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectMultilineDto> update(@PathVariable UUID id, @RequestBody UpdateProjectMultilineDto updateProjectMultilineDto) {
        return ResponseEntity.ok(projectMultilineService.update(id, updateProjectMultilineDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectMultilineService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
