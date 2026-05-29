package kg.geoinfo.system.geoabstraction.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geoabstraction.config.audit.AuditableCustom;
import kg.geoinfo.system.geoabstraction.models.enums.GeoAbstractJobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "geo_abstract_jobs", schema = "geoabstraction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoAbstractJob extends AuditableCustom<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private GeoAbstractJobStatus status;

    @Column(name = "task_type", length = 50)
    private String taskType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "characteristics", columnDefinition = "jsonb")
    private Map<String, Object> characteristics;

    @Column(name = "source_bucket")
    private String sourceBucket;

    @Column(name = "source_object_key")
    private String sourceObjectKey;

    @Column(name = "output_bucket")
    private String outputBucket;

    @Column(name = "output_prefix")
    private String outputPrefix;

    @Column(name = "crs", length = 50)
    private String crs;

    @Column(name = "bbox", columnDefinition = "geometry(MultiPolygon, 4326)")
    private MultiPolygon bbox;

    @Column(name = "min_height")
    private Double minHeight;

    @Column(name = "max_height")
    private Double maxHeight;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "error_message")
    private String errorMessage;
}
