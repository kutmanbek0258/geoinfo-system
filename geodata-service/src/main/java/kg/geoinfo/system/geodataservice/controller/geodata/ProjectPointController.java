package kg.geoinfo.system.geodataservice.controller.geodata;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/geodata/points")
@RequiredArgsConstructor
public class ProjectPointController {

    private final ProjectPointService projectPointService;

    @PostMapping
    public ResponseEntity<ProjectPointDto> create(@RequestBody CreateProjectPointDto createProjectPointDto) {
        return new ResponseEntity<>(projectPointService.create(createProjectPointDto), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectPointDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectPointService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<ProjectPointDto>> findAll() {
        return ResponseEntity.ok(projectPointService.findAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectPointDto> update(@PathVariable UUID id, @RequestBody UpdateProjectPointDto updateProjectPointDto) {
        return ResponseEntity.ok(projectPointService.update(id, updateProjectPointDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectPointService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
