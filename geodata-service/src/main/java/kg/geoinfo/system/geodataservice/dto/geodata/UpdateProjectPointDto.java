package kg.geoinfo.system.geodataservice.dto.geodata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.bedatadriven.jackson.datatype.jts.serialization.GeometryDeserializer;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Data
@NoArgsConstructor
public class UpdateProjectPointDto {
    private String name;
    private String description;
    private Status status;

    @JsonDeserialize(using = GeometryDeserializer.class)
    private Point geom;
}
