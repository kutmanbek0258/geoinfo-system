package kg.geoinfo.system.geoabstraction.dto;

import kg.geoinfo.system.geoabstraction.models.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ImageryLayerDto extends AbstractDto<UUID> {
    private UUID id;
    private UUID jobId;
    private String name;
    private String description;
    private String workspace;
    private String layerName;
    private String serviceUrl;
    private Status status;
    private String style;
    private Date dateCaptured;
    private String crs;
    private Map<String, Object> characteristics;
    private String cogObjectKey;
}
