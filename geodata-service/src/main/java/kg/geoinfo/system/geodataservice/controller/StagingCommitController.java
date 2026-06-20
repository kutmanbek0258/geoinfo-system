package kg.geoinfo.system.geodataservice.controller;

import kg.geoinfo.system.geodataservice.dto.CommitAnalysisTaskDto;
import kg.geoinfo.system.geodataservice.service.AnalysisCommitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Контроллер операций Commit и Rollback для временных результатов геоанализа.
 * Доступ ограничен ролями ADMIN и EDITOR.
 */
@RestController
@RequestMapping("/api/geodata/staging")
@RequiredArgsConstructor
public class StagingCommitController {

    private final AnalysisCommitService commitService;

    /**
     * Переносит результаты задачи taskId в постоянные векторные слои проекта.
     * Временные данные после переноса удаляются.
     */
    @PostMapping("/{taskId}/commit")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EDITOR')")
    public ResponseEntity<Void> commit(
            @PathVariable UUID taskId,
            @RequestBody CommitAnalysisTaskDto dto) {
        commitService.commitTask(
                taskId,
                dto.getProjectId(),
                dto.getFolderId(),
                dto.getTaskName() != null ? dto.getTaskName() : "Analysis " + taskId
        );
        return ResponseEntity.ok().build();
    }

    /**
     * Удаляет временные данные задачи taskId без переноса в постоянные слои.
     */
    @DeleteMapping("/{taskId}/rollback")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EDITOR')")
    public ResponseEntity<Void> rollback(@PathVariable UUID taskId) {
        commitService.rollbackTask(taskId);
        return ResponseEntity.noContent().build();
    }
}
