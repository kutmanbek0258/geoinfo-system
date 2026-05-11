package kg.geoinfo.system.terrainservice.service.kafka;

import kg.geoinfo.system.common.TerrainJobEvent;
import kg.geoinfo.system.terrainservice.service.TerrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final TerrainService terrainService;

    @KafkaListener(topics = "terrain.data.events", groupId = "${spring.kafka.consumer.group-id:terrain-service-group}")
    public void listen(TerrainJobEvent event) {
        log.info("Received terrain job event: {} for job {}", event.getEventType(), event.getJobId());

        if (event.getEventType() == TerrainJobEvent.EventType.READY || event.getEventType() == TerrainJobEvent.EventType.FAILED) {
            String terrainUrl = null;
            if (event.getEventType() == TerrainJobEvent.EventType.READY) {
                // In a real system, this URL would be constructed based on output prefix
                // We point to the directory containing layer.json
                terrainUrl = "/terrain/" + event.getOutputPrefix() + "/";
            }

            terrainService.updateJobStatus(
                    event.getJobId(),
                    event.getEventType().name(),
                    event.getErrorMessage(),
                    null, // minHeight - should be passed in event ideally
                    null, // maxHeight
                    terrainUrl
            );
        }
    }
}
