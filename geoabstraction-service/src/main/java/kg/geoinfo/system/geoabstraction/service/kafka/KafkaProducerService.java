package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;

public interface KafkaProducerService {
    void sendGeoAbstractJobEvent(GeoAbstractJobEvent event);
}
