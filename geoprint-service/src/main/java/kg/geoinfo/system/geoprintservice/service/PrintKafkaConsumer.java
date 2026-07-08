package kg.geoinfo.system.geoprintservice.service;

import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import kg.geoinfo.system.geoprintservice.model.PrintTask;
import kg.geoinfo.system.geoprintservice.repository.PrintTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintKafkaConsumer {

    private final PrintOrchestrator printOrchestrator;
    private final PrintTaskRepository printTaskRepository;
    private final MinioService minioService;

    @KafkaListener(topics = "geo.print.tasks", groupId = "${spring.kafka.consumer.group-id:geoprint-group}")
    public void consumePrintTask(Map<String, Object> message) {
        try {
            String taskIdStr = (String) message.get("taskId");
            UUID taskId = UUID.fromString(taskIdStr);
            log.info("Received Kafka message for print task: {}", taskId);
            printOrchestrator.processPrintTask(taskId);
        } catch (Exception e) {
            log.error("Error consuming Kafka print task message", e);
        }
    }

    @KafkaListener(topics = "geo.print.results", groupId = "${spring.kafka.consumer.group-id-results:geoprint-group-results}")
    @Transactional
    public void consumePrintResult(Map<String, Object> message) {
        try {
            String taskIdStr = (String) message.get("taskId");
            UUID taskId = UUID.fromString(taskIdStr);
            String status = (String) message.get("status");
            String s3Key = (String) message.get("s3Key");
            String errorMessage = (String) message.get("errorMessage");

            log.info("Received Kafka print result for task {}: Status {}", taskId, status);

            PrintTask task = printTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found for results: " + taskId));

            if ("COMPLETED".equals(status)) {
                String s3Url = minioService.getPresignedUrl(s3Key);
                task.setS3Url(s3Url);
                task.setStatus(PrintTaskStatus.COMPLETED);
                log.info("Print task {} completed successfully. PDF: {}", taskId, s3Url);
            } else {
                task.setStatus(PrintTaskStatus.FAILED);
                task.setErrorMessage(errorMessage != null ? errorMessage : "Unknown error in geoprint-worker");
                log.warn("Print task {} failed: {}", taskId, errorMessage);
            }
            printTaskRepository.save(task);
        } catch (Exception e) {
            log.error("Error consuming Kafka print result message", e);
        }
    }
}
