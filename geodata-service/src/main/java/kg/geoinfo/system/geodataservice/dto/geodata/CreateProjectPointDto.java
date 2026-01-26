package kg.geoinfo.system.geodataservice.dto.geodata;

import jakarta.validation.constraints.NotNull;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CreateProjectPointDto {
    @NotNull
    private UUID projectId;

    private String name;

    private String description;

    @NotNull
    private Status status;

    @NotNull
    private Point geom;
    private Map<String, Object> characteristics;
}
