package kg.geoinfo.system.authservice.dao.entity;

import jakarta.persistence.*;
import kg.geoinfo.system.authservice.dao.entity.common.VersionedBusinessEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(schema = "sso", name = "user_clients")
public class UserClient extends VersionedBusinessEntity<UUID> {

    @Id
    @Column(name = "user_client_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "deleted")
    private Boolean deleted;

}
