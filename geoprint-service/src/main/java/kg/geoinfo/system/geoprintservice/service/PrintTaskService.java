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
                .build();

        final PrintTask savedTask = printTaskRepository.save(task);
        final UUID taskId = savedTask.getId();
        log.info("Created print task: {}", taskId);

        final List<kg.geoinfo.system.common.GeoVectorExportRequest> exportRequests = new java.util.ArrayList<>();
        List<PrintSpecificationDto.LayerSpecDto> layers = spec.getLayers();
        if (layers != null) {
            for (int i = 0; i < layers.size(); i++) {
                PrintSpecificationDto.LayerSpecDto layer = layers.get(i);
                if ("VECTOR".equalsIgnoreCase(layer.getType()) && layer.getFeatures() == null) {
                    String exportPath = "temp/print/" + taskId + "/layer_" + i + ".geojson";
                    layer.setUrl("export-pending:" + exportPath);
                    
                    exportRequests.add(kg.geoinfo.system.common.GeoVectorExportRequest.builder()
                            .taskId(taskId)
                            .exportKey("layer_" + i)
                            .layerId(layer.getLayerId())
                            .projectId(spec.getProjectId())
                            .s3Destination(exportPath)
                            .pointIds(layer.getPointIds())
                            .multilineIds(layer.getMultilineIds())
                            .polygonIds(layer.getPolygonIds())
                            .build());
                }
            }
        }

        savedTask.setAttributes(objectMapper.convertValue(spec, Map.class));
        printTaskRepository.save(savedTask);

        final String taskIdStr = taskId.toString();
        final boolean requiresVectorExport = !exportRequests.isEmpty();

        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (requiresVectorExport) {
                        for (kg.geoinfo.system.common.GeoVectorExportRequest req : exportRequests) {
                            kafkaTemplate.send("geo.vector.export", req.getTaskId().toString(), req);
                            log.info("Sent vector export request for print task {} key {}", req.getTaskId(), req.getExportKey());
                        }
                    } else {
                        Map<String, Object> event = Map.of(
                            "taskId", taskIdStr,
                            "spec", savedTask.getAttributes()
                        );
                        kafkaTemplate.send("geo.print.tasks", taskIdStr, event);
                        log.info("Sent print task event directly to worker for task: {}", taskIdStr);
                    }
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
