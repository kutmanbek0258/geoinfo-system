package kg.geoinfo.system.geoabstraction.dto;

import kg.geoinfo.system.geoabstraction.models.enums.GeoAbstractJobStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class GeoAbstractJobDto extends AbstractDto {
    private UUID id;
    private String name;
    private GeoAbstractJobStatus status;
    private String taskType;
    private Map<String, Object> characteristics;
    private String sourceBucket;
    private String sourceObjectKey;
    private String outputBucket;
    private String outputPrefix;
    private String crs;
    private Double minHeight;
    private Double maxHeight;
    private Long fileSize;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
