
package kg.geoinfo.system.searchservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoObjectIndex {

    private String id;
    private UUID projectId;
    private String name;
    private String description;
    private String type;
    private Object geometry; // Represents Elasticsearch geo_shape
    private Map<String, Object> characteristics;
    private Integer document_count;
    private Date created_at;

}
