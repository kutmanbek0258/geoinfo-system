package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geodataservice.config.audit.AuditableCustom;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Table(name = "layers", schema = "geodata", indexes = {
        @Index(name = "ix_layers_project", columnList = "project_id")
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Layer extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LayerType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> characteristics;
}
