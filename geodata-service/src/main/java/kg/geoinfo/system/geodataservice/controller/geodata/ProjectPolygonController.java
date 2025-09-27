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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/polygons")
@RequiredArgsConstructor
public class ProjectPolygonController {

    private final ProjectPolygonService projectPolygonService;

    @PostMapping
    public ResponseEntity<ProjectPolygonDto> create(@RequestBody CreateProjectPolygonDto createProjectPolygonDto) {
        return new ResponseEntity<>(projectPolygonService.create(createProjectPolygonDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectPolygonDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectPolygonService.findById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProjectPolygonDto>> findAll(@PageableDefault(sort = "createdDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(projectPolygonService.findAll(pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectPolygonDto> update(@PathVariable UUID id, @RequestBody UpdateProjectPolygonDto updateProjectPolygonDto) {
        return ResponseEntity.ok(projectPolygonService.update(id, updateProjectPolygonDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectPolygonService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
