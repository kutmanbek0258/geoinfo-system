package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ProjectRaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRasterRepository extends JpaRepository<ProjectRaster, UUID> {
    List<ProjectRaster> findAllByLayerId(UUID layerId);
    List<ProjectRaster> findAllByFolderId(UUID folderId);
    List<ProjectRaster> findAllByLayerProjectId(UUID projectId);
}

