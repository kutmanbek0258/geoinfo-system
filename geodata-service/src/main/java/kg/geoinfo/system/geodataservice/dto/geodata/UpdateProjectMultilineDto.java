package kg.geoinfo.system.geodataservice.dto.geodata;

import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.MultiLineString;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class UpdateProjectMultilineDto {
    private String name;
    private String description;
    private UUID folderId;
    private Status status;

    private MultiLineString geom;
    private Map<String, Object> characteristics;
}
