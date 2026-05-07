package kg.geoinfo.system.terrainservice.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class TerrainLayerDto extends AbstractDto {
    private UUID id;
    private UUID jobId;
    private UUID projectId;
    private String title;
    private String description;
    private String terrainUrl;
    private String status;
    private Boolean isVisible;
    private Instant createdAt;
}
