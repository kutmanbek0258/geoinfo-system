package kg.geoinfo.system.geodataservice.models;

import jakarta.persistence.*;
import kg.geoinfo.system.geodataservice.config.audit.AuditableCustom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Table(name = "projects", schema = "geodata", indexes = {
        @Index(name = "ix_projects_name", columnList = "name")
})
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Project extends AuditableCustom<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectAccess> accesses = new HashSet<>();
}