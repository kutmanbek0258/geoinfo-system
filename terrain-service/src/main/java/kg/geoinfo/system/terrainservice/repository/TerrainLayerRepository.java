package kg.geoinfo.system.terrainservice.repository;

import kg.geoinfo.system.terrainservice.models.TerrainLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface TerrainLayerRepository extends JpaRepository<TerrainLayer, UUID> {}
