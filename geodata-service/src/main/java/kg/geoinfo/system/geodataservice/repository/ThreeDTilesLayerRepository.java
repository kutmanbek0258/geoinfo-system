package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ThreeDTilesLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThreeDTilesLayerRepository extends JpaRepository<ThreeDTilesLayer, UUID> {

    Optional<ThreeDTilesLayer> findByJobId(UUID jobId);
}
