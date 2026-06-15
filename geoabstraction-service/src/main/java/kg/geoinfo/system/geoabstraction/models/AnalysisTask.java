package kg.geoinfo.system.geoabstraction.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geoabstraction.config.audit.AuditableCustom;
import kg.geoinfo.system.geoabstraction.models.enums.AnalysisTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analysis_tasks", schema = "analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTask extends AuditableCustom<String> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "plugin_name", nullable = false, length = 100)
    private String pluginName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisTaskStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_params", columnDefinition = "jsonb")
    private Map<String, Object> inputParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "s3_input_paths", columnDefinition = "jsonb")
    private Map<String, String> s3InputPaths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "s3_output_paths", columnDefinition = "jsonb")
    private Map<String, String> s3OutputPaths;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;
}
