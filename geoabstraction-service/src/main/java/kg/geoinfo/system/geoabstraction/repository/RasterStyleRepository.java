package kg.geoinfo.system.geoabstraction.repository;

import kg.geoinfo.system.geoabstraction.models.RasterStyle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RasterStyleRepository extends JpaRepository<RasterStyle, UUID> {
    Optional<RasterStyle> findByName(String name);

    @Query("SELECT r FROM RasterStyle r WHERE " +
            "(:title IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%'))) AND " +
            "(:name IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))")
    Page<RasterStyle> findByCondition(String name, String title, Pageable pageable);
}
