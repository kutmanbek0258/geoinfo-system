package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.RasterStyle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RasterStyleRepository extends JpaRepository<RasterStyle, UUID> {

    @Query("SELECT r FROM RasterStyle r WHERE " +
           "(:name IS NULL OR LOWER(CAST(r.name AS string)) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND " +
           "(:title IS NULL OR LOWER(CAST(r.title AS string)) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%')))")
    Page<RasterStyle> findByCondition(@Param("name") String name, @Param("title") String title, Pageable pageable);

    Optional<RasterStyle> findByName(String name);
}
