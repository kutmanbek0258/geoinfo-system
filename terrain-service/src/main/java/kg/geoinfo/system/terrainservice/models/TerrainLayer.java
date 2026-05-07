package kg.geoinfo.system.terrainservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.terrainservice.config.audit.AuditableCustom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "terrain_layers", schema = "terrain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerrainLayer extends AuditableCustom<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private TerrainJob job;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "terrain_url", length = 512)
    private String terrainUrl;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "is_visible")
    private Boolean isVisible = true;
}
