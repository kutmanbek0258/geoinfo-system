package kg.geoinfo.system.streamservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaMtxSourceDto {
    private String type;
    private String id;
}
