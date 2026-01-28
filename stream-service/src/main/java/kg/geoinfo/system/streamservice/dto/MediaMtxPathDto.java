package kg.geoinfo.system.streamservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaMtxPathDto {
    private String name;
    // We only need to check for existence, so other fields are not necessary for now.
}
