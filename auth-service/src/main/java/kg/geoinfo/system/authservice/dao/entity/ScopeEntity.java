package kg.geoinfo.system.authservice.dao.entity;

import jakarta.persistence.*;
import kg.geoinfo.system.authservice.dao.entity.common.VersionedBusinessEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "sso", name = "scopes")
public class ScopeEntity extends VersionedBusinessEntity<UUID> {

    @Id
    @Column(name = "scope_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scope_code", nullable = false)
    private String code;

    @Column(name = "scope_description", nullable = false)
    private String description;

    @Column(name = "system_code", nullable = false)
    private String systemCode;

    @Column(name = "active")
    private Boolean active;

    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

}
