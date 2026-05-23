package kg.geoinfo.system.geoabstraction.repository;

import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImageryLayerRepository extends JpaRepository<ImageryLayer, UUID> {
    Page<ImageryLayer> findAllByNameContainingIgnoreCase(String name, Pageable pageable);
    java.util.Optional<ImageryLayer> findByJobId(UUID jobId);
}
