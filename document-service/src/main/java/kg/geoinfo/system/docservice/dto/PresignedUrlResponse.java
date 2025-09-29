package kg.geoinfo.system.docservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PresignedUrlResponse {
    private String url;
    private long expiresInSeconds;
}