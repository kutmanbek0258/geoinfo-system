
package kg.geoinfo.system.geodataservice.service.kafka;

import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.common.GeoVectorExportResponse;

import java.util.Map;

public interface KafkaProducerService {

    void sendGeoObjectEvent(Map<String, Object> payload, GeoObjectEvent.EventType eventType);
    void sendGeoVectorExportResponse(GeoVectorExportResponse response);

}
