package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.RasterLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RasterLayerRepository extends JpaRepository<RasterLayer, UUID> {
}
