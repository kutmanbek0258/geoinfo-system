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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public PrintTaskDto createPrintTask(PrintSpecificationDto spec) {
        PrintTask task = PrintTask.builder()
                .status(PrintTaskStatus.PENDING)
                .layout(spec.getLayout())
                .attributes(objectMapper.convertValue(spec, Map.class))
                .build();

        task = printTaskRepository.save(task);
        log.info("Created print task: {}", task.getId());

        final String taskIdStr = task.getId().toString();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Map<String, Object> event = Map.of("taskId", taskIdStr);
                    kafkaTemplate.send("geo.print.tasks", taskIdStr, event);
                }
            }
        );

        return printTaskMapper.toDto(task);
    }

    public PrintTaskDto getPrintTask(UUID id) {
        return printTaskRepository.findById(id)
                .map(printTaskMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Print task not found: " + id));
    }
}
