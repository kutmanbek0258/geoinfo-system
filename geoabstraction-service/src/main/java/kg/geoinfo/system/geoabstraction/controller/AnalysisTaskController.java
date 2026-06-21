package kg.geoinfo.system.geoabstraction.controller;

import kg.geoinfo.system.geoabstraction.dto.CommitAnalysisTaskRequestDto;
import kg.geoinfo.system.geoabstraction.dto.AnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.dto.CreateAnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.service.AnalysisTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/tasks/{id}/outputs/{outputKey}/presigned-url")
    public ResponseEntity<Map<String, String>> getOutputPresignedUrl(@PathVariable UUID id, @PathVariable String outputKey) {
        String url = service.generateOutputPresignedUrl(id, outputKey);
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{id}/commit")
    public ResponseEntity<Void> commitTask(@PathVariable UUID id, @RequestBody CommitAnalysisTaskRequestDto dto) {
        service.commitTask(id, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{id}/reject")
    public ResponseEntity<Void> rollbackTask(@PathVariable UUID id) {
        service.rollbackTask(id);
        return ResponseEntity.ok().build();
    }
}
