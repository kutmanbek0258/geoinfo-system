package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geodataservice.config.audit.AuditableCustom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "terrain_layers", schema = "geodata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerrainLayer extends AuditableCustom<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "output_prefix", length = 512)
    private String outputPrefix;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "terrain_url", length = 512)
    private String terrainUrl;

    @Column(name = "cog_object_key", length = 512)
    private String cogObjectKey;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "is_visible")
    private Boolean isVisible = true;
}
