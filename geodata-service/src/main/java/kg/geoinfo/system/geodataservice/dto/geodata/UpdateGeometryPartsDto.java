package kg.geoinfo.system.geodataservice.dto.geodata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateGeometryPartsDto {
    private List<GeometryPartDto> parts;
}
