package kg.geoinfo.system.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoAnalysisResultEvent {
    private UUID taskId;
    private String status;
    private String resultType;
    private Map<String, String> outputs;
    private String error;
    private String timestamp;
}
