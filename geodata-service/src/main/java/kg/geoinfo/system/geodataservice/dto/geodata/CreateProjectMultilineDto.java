package kg.geoinfo.system.geodataservice.dto.geodata;

import jakarta.validation.constraints.NotNull;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.MultiLineString;

import java.util.UUID;

@Data
@NoArgsConstructor
public class CreateProjectMultilineDto {
    @NotNull
    private UUID projectId;

    private String name;

    private String description;

    @NotNull
    private Status status;

    @NotNull
    private MultiLineString geom;
}
