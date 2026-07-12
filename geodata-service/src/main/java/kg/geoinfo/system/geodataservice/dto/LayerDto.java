package kg.geoinfo.system.geodataservice.dto;

import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class LayerDto {
    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private LayerType type;
    private Map<String, Object> characteristics;
}
