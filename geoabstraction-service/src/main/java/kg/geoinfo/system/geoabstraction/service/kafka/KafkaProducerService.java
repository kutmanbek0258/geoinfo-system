package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.common.GeoObjectEvent;

import java.util.Map;

public interface KafkaProducerService {
    void sendGeoAbstractJobEvent(GeoAbstractJobEvent event);
    void sendGeoObjectEvent(Map<String, Object> payload, GeoObjectEvent.EventType eventType);
}
