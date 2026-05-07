package kg.geoinfo.system.terrainservice.repository;

import kg.geoinfo.system.terrainservice.models.TerrainJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TerrainJobRepository extends JpaRepository<TerrainJob, UUID> {
}
