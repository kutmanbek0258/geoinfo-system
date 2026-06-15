package kg.geoinfo.system.geoabstraction.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateAnalysisTaskDto {
    private String pluginName;
    private UUID projectId;
    
    // Источники данных
    private Map<String, AnalysisDataSource> inputs;
    
    // Параметры алгоритма
    private Map<String, Object> parameters;

    @Data
    public static class AnalysisDataSource {
        private SourceType type;
        private UUID id;         // ID ImageryLayer или GeoObject
        private UUID taskId;     // Если результат предыдущей задачи
        private String outputKey; // Ключ в outputs предыдущей задачи
        private String s3Url;    // Прямая ссылка (если уже в S3)
    }

    public enum SourceType {
        IMAGERY_LAYER,
        VECTOR_LAYER,
        TERRAIN_LAYER,
        PREVIOUS_TASK_RESULT,
        DIRECT_S3
    }
}
