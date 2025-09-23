package kg.geoinfo.system.userservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.userservice.config.audit.AuditableCustom;
import kg.geoinfo.system.userservice.models.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Table(name = "project_points", schema = "geodata", indexes = {
        @Index(name = "ix_pp_status", columnList = "status"),
        @Index(name = "ix_pp_project", columnList = "project_id"),
        @Index(name = "ix_pp_geom", columnList = "geom")
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectPoint extends AuditableCustom<String> {
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
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point geom;
}