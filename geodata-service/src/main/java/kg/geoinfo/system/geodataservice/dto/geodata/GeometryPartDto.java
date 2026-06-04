package kg.geoinfo.system.geodataservice.dto.geodata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeometryPartDto {
    private Integer subId;
    private String geojson;
}
