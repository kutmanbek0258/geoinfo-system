package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geodataservice.config.audit.AuditableCustom;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Table(name = "raster_styles", schema = "geodata")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RasterStyle extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "type", nullable = false, length = 50)
    private String type = "ramp";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> config;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;
}
