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
public class GeoAnalysisTaskEvent {
    private UUID taskId;
    private String pluginName;
    private Map<String, String> inputs;
    private Map<String, Object> parameters;
}
