package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.repository.projection.GeometryPartProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectMultilineRepository extends JpaRepository<ProjectMultiline, UUID> {
    Page<ProjectMultiline> findAllByProjectId(Pageable pageable, UUID projectId);
    List<ProjectMultiline> findAllByFolderId(UUID folderId);
    List<ProjectMultiline> findAllByProjectIdAndFolderIdIsNull(UUID projectId);
    List<ProjectMultiline> findAllByLayerId(UUID layerId);
    List<ProjectMultiline> findAllByLayerIdAndFolderIdIsNull(UUID layerId);

    @Query(value = "SELECT (parts.path)[1] as subId, ST_AsGeoJSON(parts.geom) as geojson " +
            "FROM (SELECT (ST_Dump(geom)).* FROM geodata.project_multilines WHERE id = :id) as parts " +
            "WHERE ST_Intersects(parts.geom, ST_MakeEnvelope(:minX, :minY, :maxX, :maxY, 4326))", nativeQuery = true)
    List<GeometryPartProjection> findPartsInBBox(@Param("id") UUID id, @Param("minX") double minX, @Param("minY") double minY, @Param("maxX") double maxX, @Param("maxY") double maxY);

    @Modifying
    @Query(value = "UPDATE geodata.project_multilines SET geom = (" +
            "SELECT ST_Collect(CASE WHEN (parts.path)[1] = :subId THEN ST_Force3D(ST_GeomFromText(:wkt, 4326)) ELSE parts.geom END ORDER BY (parts.path)[1]) " +
            "FROM (SELECT (ST_Dump(geom)).* FROM geodata.project_multilines WHERE id = :id) as parts" +
            ") WHERE id = :id", nativeQuery = true)
    void updatePart(@Param("id") UUID id, @Param("subId") int subId, @Param("wkt") String wkt);
}
