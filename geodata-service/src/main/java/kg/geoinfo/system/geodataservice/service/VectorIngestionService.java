package kg.geoinfo.system.geodataservice.service;

import java.util.Map;

public interface VectorIngestionService {
    void processVectorImport(Map<String, Object> payload);
}
