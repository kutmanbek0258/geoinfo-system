package kg.geoinfo.system.geodataservice.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class RasterStyleDto {
    private UUID id;
    private String name;
    private String title;
    private String type;
    private List<Map<String, Object>> config;
    private boolean isSystem;
}
