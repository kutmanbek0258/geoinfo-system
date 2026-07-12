package kg.geoinfo.system.geoabstraction.service.kafka;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.common.GeoAnalysisTaskEvent;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.common.GeoVectorExportRequest;

import java.util.Map;

public interface KafkaProducerService {
    void sendGeoAbstractJobEvent(GeoAbstractJobEvent event);
    void sendGeoObjectEvent(Map<String, Object> payload, GeoObjectEvent.EventType eventType);
    void sendGeoAnalysisTaskEvent(GeoAnalysisTaskEvent event);
    void sendVectorExportRequest(GeoVectorExportRequest request);
    void sendRasterProcessedEvent(Map<String, Object> payload);
    void sendTerrainProcessedEvent(Map<String, Object> payload);
}
