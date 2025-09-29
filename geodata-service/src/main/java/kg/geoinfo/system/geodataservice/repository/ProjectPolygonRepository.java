package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ProjectPolygon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectPolygonRepository extends JpaRepository<ProjectPolygon, UUID> {
    Page<ProjectPolygon> findAllByProjectId(Pageable pageable, UUID projectId);
}
