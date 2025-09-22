package kg.geoinfo.system.authservice.dao.repository;

import kg.geoinfo.system.authservice.dao.entity.ScopeVWEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScopeVWRepository extends JpaRepository<ScopeVWEntity, UUID> {

    ScopeVWEntity findByUniqueCode(String uniqueCode);

    List<ScopeVWEntity> findAllByUniqueCodeIn(Collection<String> uniqueCodes);
}
