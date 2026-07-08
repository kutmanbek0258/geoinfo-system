package kg.geoinfo.system.geoprintservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geoprintservice.dto.PrintSpecificationDto;
import kg.geoinfo.system.geoprintservice.dto.PrintTaskDto;
import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import kg.geoinfo.system.geoprintservice.mapper.PrintTaskMapper;
import kg.geoinfo.system.geoprintservice.model.PrintTask;
import kg.geoinfo.system.geoprintservice.repository.PrintTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTaskService {

    private final PrintTaskRepository printTaskRepository;
    private final PrintTaskMapper printTaskMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;

    @Transactional
    public PrintTaskDto createPrintTask(PrintSpecificationDto spec) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        // Механизм удаления: один файл на проект от одного пользователя
        if (spec.getProjectId() != null) {
            log.info("Checking for existing print tasks for project {} and user {}", spec.getProjectId(), currentUser);
            List<PrintTask> existingTasks = printTaskRepository.findByProjectIdAndCreatedBy(spec.getProjectId(), currentUser);
            for (PrintTask oldTask : existingTasks) {
                try {
                    String fileName = "reports/" + oldTask.getId() + ".pdf";
                    minioService.deleteFile(fileName);
                    printTaskRepository.delete(oldTask);
                    log.info("Cleaned up old print task {} for project {}", oldTask.getId(), spec.getProjectId());
                } catch (Exception e) {
                    log.warn("Failed to cleanup old task {}: {}", oldTask.getId(), e.getMessage());
                }
            }
        }

        PrintTask task = PrintTask.builder()
                .status(PrintTaskStatus.PENDING)
                .projectId(spec.getProjectId())
                .layout(spec.getLayout())
                .attributes(objectMapper.convertValue(spec, Map.class))
                .build();

        final PrintTask savedTask = printTaskRepository.save(task);
        log.info("Created print task: {}", savedTask.getId());

        final String taskIdStr = savedTask.getId().toString();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Map<String, Object> event = Map.of(
                        "taskId", taskIdStr,
                        "spec", savedTask.getAttributes()
                    );
                    kafkaTemplate.send("geo.print.tasks", taskIdStr, event);
                }
            }
        );

        return printTaskMapper.toDto(savedTask);
    }

    public PrintTaskDto getPrintTask(UUID id) {
        return printTaskRepository.findById(id)
                .map(printTaskMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Print task not found: " + id));
    }
}
