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

        Map<String, Object> event = Map.of("taskId", task.getId().toString());
        kafkaTemplate.send("geo.print.tasks", task.getId().toString(), event);

        return printTaskMapper.toDto(task);
    }

    public PrintTaskDto getPrintTask(UUID id) {
        return printTaskRepository.findById(id)
                .map(printTaskMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Print task not found: " + id));
    }
}
