package kg.geoinfo.system.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoVectorExportResponse {
    private UUID taskId;
    private String exportKey;
    private boolean success;
    private String s3Url;
    private String error;
}
