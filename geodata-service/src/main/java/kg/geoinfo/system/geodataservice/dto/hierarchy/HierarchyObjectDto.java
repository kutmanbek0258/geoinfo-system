package kg.geoinfo.system.geodataservice.dto.hierarchy;

import com.fasterxml.jackson.annotation.JsonInclude;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HierarchyObjectDto {
    private UUID id;
    private String name;
    private String description;
    private String type; // "Point", "MultiLineString", "Polygon", "Raster"
    private Status status;
    private UUID folderId;
    private UUID layerId;
    private UUID projectId;

    // Vector specific fields
    private Object geom;
    private Object bbox;

    // Raster specific fields
    private String cogObjectKey;
    private String crs;
    private String colormapId;
    private String resampling;
    private Map<String, Object> characteristics;
}
