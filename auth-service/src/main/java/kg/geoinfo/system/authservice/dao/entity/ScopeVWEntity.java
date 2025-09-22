package kg.geoinfo.system.authservice.dao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "sso", name = "scopes_vw")
public class ScopeVWEntity {

    @Id
    @Column(name = "scope_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scope_unique_code", nullable = false)
    private String uniqueCode;

    @Column(name = "scope_description", nullable = false)
    private String description;

    @Column(name = "system_code", nullable = false)
    private String systemCode;

    @Column(name = "active")
    private Boolean active;

}
