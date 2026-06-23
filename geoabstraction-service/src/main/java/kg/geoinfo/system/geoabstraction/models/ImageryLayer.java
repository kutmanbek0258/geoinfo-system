package kg.geoinfo.system.geoabstraction.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geoabstraction.config.audit.AuditableCustom;
import kg.geoinfo.system.geoabstraction.models.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Table(name = "imagery_layers", schema = "geoabstraction", indexes = {
        @Index(name = "ix_imagery_date", columnList = "date_captured")
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ImageryLayer extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "layer_name", length = 256)
    private String layerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "style_id")
    private RasterStyle style;

    @Column(name = "date_captured", nullable = false)
    private Date dateCaptured;

    @Column(name = "crs", nullable = false, length = 32)
    private String crs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "characteristics", columnDefinition = "jsonb")
    private Map<String, Object> characteristics;

    @Column(name = "cog_object_key", length = 512)
    private String cogObjectKey;

    @Column(name = "bbox", columnDefinition = "geometry(MultiPolygon, 4326)")
    private MultiPolygon bbox;
}
