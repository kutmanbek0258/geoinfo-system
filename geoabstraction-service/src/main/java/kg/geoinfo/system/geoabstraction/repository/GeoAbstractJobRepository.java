package kg.geoinfo.system.geoabstraction.repository;

import feign.Param;
import kg.geoinfo.system.geoabstraction.models.GeoAbstractJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GeoAbstractJobRepository extends JpaRepository<GeoAbstractJob, UUID> {
    @org.springframework.data.jpa.repository.Query("SELECT j FROM GeoAbstractJob j WHERE " +
            "(:projectId IS NOT NULL AND j.projectId = :projectId) OR " +
            "(:projectId IS NULL AND j.projectId IS NULL)")
    Page<GeoAbstractJob> findByProjectId(@Param("projectId") UUID projectId, Pageable pageable);
}
