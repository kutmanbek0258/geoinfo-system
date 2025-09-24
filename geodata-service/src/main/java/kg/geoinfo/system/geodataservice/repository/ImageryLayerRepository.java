package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ImageryLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ImageryLayerRepository extends JpaRepository<ImageryLayer, UUID>, JpaSpecificationExecutor<ImageryLayer> {
}