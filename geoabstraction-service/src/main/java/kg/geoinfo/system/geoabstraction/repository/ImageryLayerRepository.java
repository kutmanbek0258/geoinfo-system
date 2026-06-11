package kg.geoinfo.system.geoabstraction.repository;

import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImageryLayerRepository extends JpaRepository<ImageryLayer, UUID> {
    @org.springframework.data.jpa.repository.Query("SELECT i FROM ImageryLayer i WHERE " +
            "(:name IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND " +
            "(:projectId IS NULL AND i.projectId IS NULL OR i.projectId = :projectId OR i.projectId IS NULL)")
    Page<ImageryLayer> findByCondition(String name, UUID projectId, Pageable pageable);

    java.util.Optional<ImageryLayer> findByJobId(UUID jobId);
}
