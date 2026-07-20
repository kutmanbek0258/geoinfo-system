package kg.geoinfo.system.geodataservice.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ThreeDTilesLayerDto extends AbstractDto {
    private UUID id;
    private UUID jobId;
    private String title;
    private String description;
    private String tilesetUrl;
    private String sourceObjectKey;
    private String status;
    private String outputPrefix;
    private Boolean isVisible;
    private Instant createdAt;
}
