package kg.geoinfo.system.geodataservice.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class TerrainLayerDto extends AbstractDto {
    private UUID id;
    private UUID jobId;
    private String title;
    private String description;
    private String terrainUrl;
    private String cogObjectKey;
    private String cogUrl;
    private String status;
    private String outputPrefix;
    private Boolean isVisible;
    private Instant createdAt;
}
