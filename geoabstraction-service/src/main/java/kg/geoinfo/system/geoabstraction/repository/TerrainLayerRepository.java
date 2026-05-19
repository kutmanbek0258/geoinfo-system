package kg.geoinfo.system.geoabstraction.repository;

import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TerrainLayerRepository extends JpaRepository<TerrainLayer, UUID> {
    boolean existsByJobId(UUID jobId);
}
