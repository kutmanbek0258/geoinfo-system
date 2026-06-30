package kg.geoinfo.system.geoabstraction.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "plugin_schemas", schema = "analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PluginSchema {

    @Id
    @Column(name = "plugin_name", nullable = false, length = 100)
    private String pluginName;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "icon")
    private String icon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema", columnDefinition = "jsonb")
    private Map<String, Object> schema;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;
}
