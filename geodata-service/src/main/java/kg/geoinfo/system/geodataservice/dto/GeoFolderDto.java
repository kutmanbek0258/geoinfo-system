package kg.geoinfo.system.geodataservice.dto;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class GeoFolderDto {
    private UUID id;
    private UUID layerId;
    private UUID parentId;
    private String name;
    private String description;
    private Map<String, Object> characteristics;
}
