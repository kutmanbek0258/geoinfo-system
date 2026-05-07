package kg.geoinfo.system.terrainservice.dto;

import kg.geoinfo.system.terrainservice.models.enums.TerrainJobStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class TerrainJobDto extends AbstractDto {
    private UUID id;
    private UUID projectId;
    private String name;
    private TerrainJobStatus status;
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
