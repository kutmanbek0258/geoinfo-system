package kg.geoinfo.system.geoabstraction.repository;

import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TerrainLayerRepository extends JpaRepository<TerrainLayer, UUID> {
    boolean existsByJobId(UUID jobId);
    Optional<TerrainLayer> findByJobId(UUID jobId);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM TerrainLayer t WHERE " +
            "(:projectId IS NULL AND t.projectId IS NULL OR t.projectId = :projectId OR t.projectId IS NULL)")
    org.springframework.data.domain.Page<TerrainLayer> findByProjectId(java.util.UUID projectId, org.springframework.data.domain.Pageable pageable);
}
