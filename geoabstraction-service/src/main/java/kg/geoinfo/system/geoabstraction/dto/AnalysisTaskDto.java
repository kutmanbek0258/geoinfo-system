package kg.geoinfo.system.geoabstraction.dto;

import kg.geoinfo.system.geoabstraction.models.enums.AnalysisTaskStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class AnalysisTaskDto extends AbstractDto {
    private UUID id;
    private String pluginName;
    private AnalysisTaskStatus status;
    private Map<String, Object> inputParams;
    private Map<String, String> s3InputPaths;
    private Map<String, String> s3OutputPaths;
    private String errorMessage;
    private UUID userId;
    private UUID projectId;
}
