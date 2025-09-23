package kg.geoinfo.system.userservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.userservice.config.audit.AuditableCustom;
import kg.geoinfo.system.userservice.models.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Table(name = "imagery_layers", schema = "geodata", indexes = {
        @Index(name = "ix_imagery_date", columnList = "date_captured"),
        @Index(name = "ux_imagery_ws_name", columnList = "workspace, layer_name", unique = true)
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ImageryLayer extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "workspace", nullable = false, length = 128)
    private String workspace;

    @Column(name = "layer_name", nullable = false, length = 256)
    private String layerName;

    @Column(name = "service_url", nullable = false)
    private String serviceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "style", length = 128)
    private String style;

    @Column(name = "date_captured", nullable = false)
    private Date dateCaptured;

    @Column(name = "crs", nullable = false, length = 32)
    private String crs;
}