package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.locationtech.jts.geom.Polygon;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p LEFT JOIN p.accesses pa WHERE p.createdBy = :email OR pa.id.userEmail = :email")
    Page<Project> findAllExtended(@Param("email") String email, Pageable pageable);

    @Query(value = "SELECT ST_SetSRID(ST_Envelope(ST_Collect(geom)), 4326) FROM (" +
            "  SELECT geom FROM geodata.project_points WHERE project_id = :projectId " +
            "  UNION ALL " +
            "  SELECT geom FROM geodata.project_multilines WHERE project_id = :projectId " +
            "  UNION ALL " +
            "  SELECT geom FROM geodata.project_polygons WHERE project_id = :projectId " +
            ") as all_geoms", nativeQuery = true)
    Polygon calculateProjectBBox(@Param("projectId") UUID projectId);
}