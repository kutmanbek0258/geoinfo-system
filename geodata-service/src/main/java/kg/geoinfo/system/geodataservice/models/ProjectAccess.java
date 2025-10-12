package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "project_access", schema = "geodata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAccess {

    @EmbeddedId
    private ProjectAccessId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "permission_level", nullable = false)
    private String permissionLevel;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ProjectAccessId implements Serializable {
        @Column(name = "project_id")
        private UUID projectId;

        @Column(name = "user_email")
        private String userEmail;
    }
}
