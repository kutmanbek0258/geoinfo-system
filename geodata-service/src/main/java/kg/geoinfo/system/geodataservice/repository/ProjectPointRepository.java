package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectPointRepository extends JpaRepository<ProjectPoint, UUID> {
    Page<ProjectPoint> findAllByProjectId(Pageable pageable, UUID projectId);
}
