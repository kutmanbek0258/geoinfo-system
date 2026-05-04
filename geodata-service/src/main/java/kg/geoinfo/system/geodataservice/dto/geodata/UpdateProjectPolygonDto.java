package kg.geoinfo.system.geodataservice.dto.geodata;

import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Polygon;

import java.util.Map;

@Data
@NoArgsConstructor
public class UpdateProjectPolygonDto {
    private String name;
    private String description;
    private Status status;

    private Polygon geom;
    private Map<String, Object> characteristics;
}
