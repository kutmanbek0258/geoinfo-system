package kg.geoinfo.system.geoabstraction.repository;

import kg.geoinfo.system.geoabstraction.models.GeoAbstractJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GeoAbstractJobRepository extends JpaRepository<GeoAbstractJob, UUID> {
    @org.springframework.data.jpa.repository.Query("SELECT j FROM GeoAbstractJob j WHERE " +
            "(:projectId IS NULL AND j.projectId IS NULL OR j.projectId = :projectId OR j.projectId IS NULL)")
    org.springframework.data.domain.Page<GeoAbstractJob> findByProjectId(java.util.UUID projectId, org.springframework.data.domain.Pageable pageable);
}
