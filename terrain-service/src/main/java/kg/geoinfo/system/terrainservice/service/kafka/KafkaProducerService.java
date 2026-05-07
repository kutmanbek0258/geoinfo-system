package kg.geoinfo.system.terrainservice.service.kafka;

import kg.geoinfo.system.common.TerrainJobEvent;

public interface KafkaProducerService {
    void sendTerrainJobEvent(TerrainJobEvent event);
}
