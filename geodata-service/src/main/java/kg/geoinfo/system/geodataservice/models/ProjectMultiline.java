package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geodataservice.config.audit.AuditableCustom;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.MultiLineString;

import java.util.UUID;

@Table(name = "project_multilines", schema = "geodata", indexes = {
        @Index(name = "ix_ml_status", columnList = "status"),
        @Index(name = "ix_ml_geom", columnList = "geom"),
        @Index(name = "ix_ml_project", columnList = "project_id")
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProjectMultiline extends AuditableCustom<String> {
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

    @Column(columnDefinition = "geometry(MultiLineString,4326)", nullable = false)
    private MultiLineString geom;

    @Column(name = "length_m")
    private Double lengthM;
}