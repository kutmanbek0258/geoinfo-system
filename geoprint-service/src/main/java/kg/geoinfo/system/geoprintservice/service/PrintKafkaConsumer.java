package kg.geoinfo.system.geoprintservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintKafkaConsumer {

    private final PrintOrchestrator printOrchestrator;

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
}
