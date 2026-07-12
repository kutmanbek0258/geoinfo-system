package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geodataservice.config.audit.AuditableCustom;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Table(name = "raster_layers", schema = "geodata")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RasterLayer extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "cog_object_key", length = 512)
    private String cogObjectKey;

    @Column(name = "bbox", columnDefinition = "geometry(MultiPolygon, 4326)")
    private MultiPolygon bbox;

    @Column(name = "crs", length = 32)
    private String crs;

    @Column(name = "colormap_id", length = 100)
    private String colormapId;

    @Column(name = "resampling", length = 50)
    private String resampling;

    @Column(name = "date_captured")
    private Date dateCaptured;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> characteristics;
}
