package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.geoabstraction.service.GeoAbstractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final GeoAbstractionService geoAbstractionService;

    @KafkaListener(topics = {"geoabstraction.terrain.events", "geoabstraction.raster.events"}, 
                   groupId = "${spring.kafka.consumer.group-id:geoabstraction-service-group}")
    public void listen(GeoAbstractJobEvent event) {
        log.info("Received geo-abstraction job event: {} for job {} (Type: {})", 
                event.getEventType(), event.getJobId(), event.getTaskType());

        if (event.getEventType() == GeoAbstractJobEvent.EventType.READY || event.getEventType() == GeoAbstractJobEvent.EventType.FAILED) {
            String terrainUrl = null;
            if (event.getEventType() == GeoAbstractJobEvent.EventType.READY && "TERRAIN_MESH".equals(event.getTaskType())) {
                terrainUrl = "/terrain/" + event.getOutputPrefix() + "/";
            }

            geoAbstractionService.updateJobStatus(
                    event.getJobId(),
                    event.getEventType().name(),
                    event.getErrorMessage(),
                    null,
                    null,
                    terrainUrl
            );
        }
    }
}
