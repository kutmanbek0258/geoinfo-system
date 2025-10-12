package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ProjectAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectAccessRepository extends JpaRepository<ProjectAccess, ProjectAccess.ProjectAccessId> {
}
