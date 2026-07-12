package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.TerrainLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TerrainLayerRepository extends JpaRepository<TerrainLayer, UUID> {

    Optional<TerrainLayer> findByJobId(UUID jobId);
}
