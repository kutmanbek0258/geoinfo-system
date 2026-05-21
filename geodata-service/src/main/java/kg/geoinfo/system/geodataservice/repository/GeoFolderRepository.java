package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.GeoFolder;
import kg.geoinfo.system.geodataservice.models.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GeoFolderRepository extends JpaRepository<GeoFolder, UUID> {
    List<GeoFolder> findAllByProject(Project project);
    List<GeoFolder> findAllByProjectAndParentIsNull(Project project);
}
