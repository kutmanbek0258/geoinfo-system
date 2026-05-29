package kg.geoinfo.system.geodataservice.dto.geodata;

import com.fasterxml.jackson.annotation.JsonInclude;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectPolygonDto {
    private UUID id;
    private UUID projectId;
    private UUID folderId;
    private String name;
    private String description;
    private Status status;

    private MultiPolygon geom;

    private Double areaM2;
    private String imageUrl;
    private Map<String, Object> characteristics;
}
