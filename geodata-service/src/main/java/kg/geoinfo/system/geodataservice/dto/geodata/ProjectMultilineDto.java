package kg.geoinfo.system.geodataservice.dto.geodata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.bedatadriven.jackson.datatype.jts.serialization.GeometrySerializer;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.MultiLineString;

import java.util.UUID;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectMultilineDto {
    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private Status status;

    @JsonSerialize(using = GeometrySerializer.class)
    private MultiLineString geom;

    private Double lengthM;
}
