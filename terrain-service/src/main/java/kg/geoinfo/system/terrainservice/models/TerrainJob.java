package kg.geoinfo.system.terrainservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.terrainservice.config.audit.AuditableCustom;
import kg.geoinfo.system.terrainservice.models.enums.TerrainJobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

@Entity
@Table(name = "terrain_jobs", schema = "terrain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerrainJob extends AuditableCustom<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TerrainJobStatus status;

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

    @Column(name = "bbox", columnDefinition = "geometry(Polygon, 4326)")
    private Polygon bbox;

    @Column(name = "min_height")
    private Double minHeight;

    @Column(name = "max_height")
    private Double maxHeight;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "error_message")
    private String errorMessage;
}
