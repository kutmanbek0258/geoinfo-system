package kg.geoinfo.system.geoabstraction.dto;

import kg.geoinfo.system.geoabstraction.models.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ImageryLayerDto extends AbstractDto<UUID> {
    private UUID id;
    private UUID projectId;
    private UUID jobId;
    private String name;
    private String description;
    private String layerName;
    private Status status;
    private RasterStyleDto style;
    private Date dateCaptured;
    private String crs;
    private String colormapId;
    private String resampling;
    private Map<String, Object> characteristics;
    private String cogObjectKey;
    private MultiPolygon bbox;
}
