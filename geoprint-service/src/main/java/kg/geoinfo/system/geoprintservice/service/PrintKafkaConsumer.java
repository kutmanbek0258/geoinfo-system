package kg.geoinfo.system.geoprintservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoVectorExportResponse;
import kg.geoinfo.system.geoprintservice.dto.PrintSpecificationDto;
import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import kg.geoinfo.system.geoprintservice.model.PrintTask;
import kg.geoinfo.system.geoprintservice.repository.PrintTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;

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

    @KafkaListener(topics = "geo.vector.export.results", groupId = "${spring.kafka.consumer.group-id-vector-results:geoprint-vector-results-group}")
    @Transactional
    public void consumeVectorExportResult(GeoVectorExportResponse response) {
        try {
            UUID taskId = response.getTaskId();
            String exportKey = response.getExportKey();
            boolean success = response.isSuccess();
            String s3Url = response.getS3Url();
            String error = response.getError();
            String taskIdStr = taskId.toString();

            log.info("Received vector export result for task {} key {}: success={}", taskId, exportKey, success);

            PrintTask task = printTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found for vector export: " + taskId));

            if (task.getStatus() != PrintTaskStatus.PENDING) {
                log.warn("Received vector export response for task {} in status {}", taskId, task.getStatus());
                return;
            }

            if (!success) {
                task.setStatus(PrintTaskStatus.FAILED);
                task.setErrorMessage(error != null ? error : "Vector export failed");
                printTaskRepository.save(task);
                log.warn("Print task {} failed during vector export: {}", taskId, error);
                return;
            }

            // Update spec attributes in DB
            ObjectMapper mapper = new ObjectMapper();
            PrintSpecificationDto spec = mapper.convertValue(task.getAttributes(), PrintSpecificationDto.class);
            if (spec.getLayers() != null && exportKey != null && exportKey.startsWith("layer_")) {
                int layerIndex = Integer.parseInt(exportKey.replace("layer_", ""));
                if (layerIndex >= 0 && layerIndex < spec.getLayers().size()) {
                    PrintSpecificationDto.LayerSpecDto layer = spec.getLayers().get(layerIndex);
                    layer.setUrl(s3Url);
                    task.setAttributes(mapper.convertValue(spec, Map.class));
                    printTaskRepository.save(task);
                }
            }

            // Check if all vector layers are finished exporting
            boolean allReady = true;
            if (spec.getLayers() != null) {
                for (PrintSpecificationDto.LayerSpecDto layer : spec.getLayers()) {
                    if ("VECTOR".equalsIgnoreCase(layer.getType()) && layer.getFeatures() == null) {
                        if (layer.getUrl() != null && layer.getUrl().startsWith("export-pending:")) {
                            allReady = false;
                            break;
                        }
                    }
                }
            }

            if (allReady) {
                log.info("All vectors successfully exported for task {}. Triggering geoprint-worker.", taskId);
                Map<String, Object> event = Map.of(
                    "taskId", taskIdStr,
                    "spec", task.getAttributes()
                );
                kafkaTemplate.send("geo.print.tasks", taskIdStr, event);
            }

        } catch (Exception e) {
            log.error("Error consuming Kafka vector export result", e);
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
