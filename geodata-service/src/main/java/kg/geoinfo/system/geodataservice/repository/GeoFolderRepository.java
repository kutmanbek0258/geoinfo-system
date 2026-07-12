package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.GeoFolder;
import kg.geoinfo.system.geodataservice.models.Layer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GeoFolderRepository extends JpaRepository<GeoFolder, UUID> {
    List<GeoFolder> findAllByLayer(Layer layer);
    List<GeoFolder> findAllByLayerId(UUID layerId);
    List<GeoFolder> findAllByLayerProjectId(UUID projectId);
}
