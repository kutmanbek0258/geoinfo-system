
package kg.geoinfo.system.geodataservice.dto.geodata;

import com.fasterxml.jackson.annotation.JsonInclude;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectPointDto {
    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private Status status;

    private Point geom;
}
