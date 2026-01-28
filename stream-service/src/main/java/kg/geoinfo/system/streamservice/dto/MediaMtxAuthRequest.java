package kg.geoinfo.system.streamservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaMtxAuthRequest {
    private String ip;
    private String user;
    private String password;
    private String path;
    private String protocol;
    private String action;
    private String query;
}
