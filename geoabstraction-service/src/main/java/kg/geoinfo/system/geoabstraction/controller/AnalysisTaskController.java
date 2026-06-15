package kg.geoinfo.system.geoabstraction.controller;

import kg.geoinfo.system.geoabstraction.dto.AnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.dto.CreateAnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.service.AnalysisTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisTaskController {

    private final AnalysisTaskService service;

    @PostMapping("/tasks")
    public ResponseEntity<AnalysisTaskDto> createTask(@RequestBody CreateAnalysisTaskDto dto) {
        return ResponseEntity.ok(service.createTask(dto));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<AnalysisTaskDto> getTask(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getTask(id));
    }

    @GetMapping("/tasks/project/{projectId}")
    public ResponseEntity<List<AnalysisTaskDto>> getTasksByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.getTasksByProjectId(projectId));
    }
}
