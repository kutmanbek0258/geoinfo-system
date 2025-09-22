package kg.geoinfo.system.authservice.dao.repository;

import kg.geoinfo.system.authservice.dao.entity.FileStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FileStoreRepository extends JpaRepository<FileStoreEntity, UUID> {
}
