
package kg.geoinfo.system.searchservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentIndex {

    private String id;
    private String geo_object_id;
    private String file_name;
    private String description;
    private ArrayList<Object> tags;
    private String mime_type;
    private Date upload_date;
    private String content; // Optional: Full text content of the document
    private String type;

}
