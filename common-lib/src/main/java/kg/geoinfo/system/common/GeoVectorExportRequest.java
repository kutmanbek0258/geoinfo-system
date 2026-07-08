package kg.geoinfo.system.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoVectorExportRequest {
    private UUID taskId;
    private String exportKey; // Ключ в inputs аналитической задачи
    private UUID layerId;    // ID слоя/папки в geodata-service
    private UUID projectId;  // ID проекта для экспорта корневых объектов
    private String s3Destination; // Куда положить GeoJSON
    private java.util.List<UUID> pointIds;
    private java.util.List<UUID> multilineIds;
    private java.util.List<UUID> polygonIds;
}
