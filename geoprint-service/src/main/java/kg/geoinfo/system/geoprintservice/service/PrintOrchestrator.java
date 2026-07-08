package kg.geoinfo.system.geoprintservice.service;

import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import kg.geoinfo.system.geoprintservice.model.PrintTask;
import kg.geoinfo.system.geoprintservice.repository.PrintTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintOrchestrator {

    private final PrintTaskRepository printTaskRepository;

    @Transactional
    public void processPrintTask(UUID taskId) {
        PrintTask task = printTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        try {
            log.info("Starting processing print task: {}. Delegated to geoprint-worker.", taskId);
            task.setStatus(PrintTaskStatus.PROCESSING);
            printTaskRepository.save(task);
        } catch (Exception e) {
            log.error("Error starting print task: {}", taskId, e);
            task.setStatus(PrintTaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            printTaskRepository.save(task);
        }
    }
}
