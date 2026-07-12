package kg.geoinfo.system.geodataservice.dto;

import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;

import org.locationtech.jts.geom.MultiPolygon;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Data
public class ProjectRasterDto {
    private UUID id;
    private UUID layerId;
    private UUID folderId;
    private String name;
    private String description;
    private String cogObjectKey;
    private MultiPolygon bbox;
    private String crs;
    private String colormapId;
    private String resampling;
    private Date dateCaptured;
    private Status status;
    private Map<String, Object> characteristics;
}
