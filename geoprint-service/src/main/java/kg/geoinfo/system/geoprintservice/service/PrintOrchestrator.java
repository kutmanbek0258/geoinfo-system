package kg.geoinfo.system.geoprintservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geoprintservice.dto.PrintSpecificationDto;
import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import kg.geoinfo.system.geoprintservice.model.PrintTask;
import kg.geoinfo.system.geoprintservice.repository.PrintTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintOrchestrator {

    private final PrintTaskRepository printTaskRepository;
    private final MapRenderer mapRenderer;
    private final PdfBuilder pdfBuilder;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processPrintTask(UUID taskId) {
        PrintTask task = printTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        try {
            log.info("Starting processing print task: {}", taskId);
            task.setStatus(PrintTaskStatus.PROCESSING);
            printTaskRepository.save(task);

            // 1. Преобразование атрибутов в спецификацию
            PrintSpecificationDto spec = objectMapper.convertValue(task.getAttributes(), PrintSpecificationDto.class);

            // 2. Рендеринг карты
            BufferedImage mapImage = mapRenderer.renderMap(spec);

            // 3. Сборка PDF
            byte[] pdfBytes = pdfBuilder.buildPdfReport(mapImage, spec.getAttributes());

            // 4. Загрузка в MinIO
            String fileName = "reports/" + taskId + ".pdf";
            minioService.uploadFile(fileName, new ByteArrayInputStream(pdfBytes), "application/pdf");

            // 5. Получение ссылки и завершение
            String s3Url = minioService.getPresignedUrl(fileName);
            task.setS3Url(s3Url);
            task.setStatus(PrintTaskStatus.COMPLETED);
            log.info("Print task {} completed successfully", taskId);

        } catch (Exception e) {
            log.error("Error processing print task: {}", taskId, e);
            task.setStatus(PrintTaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        } finally {
            printTaskRepository.save(task);
        }
    }
}
