package kg.geoinfo.system.userservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.userservice.config.audit.AuditableCustom;
import kg.geoinfo.system.userservice.models.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

@Table(name = "project_polygons", schema = "geodata", indexes = {
        @Index(name = "ix_pg_status", columnList = "status"),
        @Index(name = "ix_pg_project", columnList = "project_id"),
        @Index(name = "ix_pg_geom", columnList = "geom")
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectPolygon extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @Column(name = "area_m2")
    private Double areaM2;

    @Column(columnDefinition = "geometry(Polygon,4326)", nullable = false)
    private Polygon geom;
}