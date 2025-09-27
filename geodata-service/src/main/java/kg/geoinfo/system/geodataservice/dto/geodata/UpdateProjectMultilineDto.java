package kg.geoinfo.system.geodataservice.dto.geodata;

import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.MultiLineString;

@Data
@NoArgsConstructor
public class UpdateProjectMultilineDto {
    private String name;
    private String description;
    private Status status;

    private MultiLineString geom;
}
