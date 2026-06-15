package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.TempAnalysisGeometry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TempAnalysisGeometryRepository extends JpaRepository<TempAnalysisGeometry, Long> {
    void deleteByTaskId(UUID taskId);
    void deleteByCreatedAtBefore(java.time.OffsetDateTime expiryTime);
}
