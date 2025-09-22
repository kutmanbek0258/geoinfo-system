package kg.geoinfo.system.authservice.dao.repository;

import kg.geoinfo.system.authservice.dao.entity.UserEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.UUID;

public interface UserEventRepository extends JpaRepository<UserEventEntity, UUID> {

    @Modifying
    @Query(value = "delete from sso.user_events where date(created_date) < :threshold", nativeQuery = true)
    void deleteAllLessThenCreationDate(LocalDate threshold);

    Page<UserEventEntity> findAllByCreatedBy(String username, PageRequest pageRequest);
}
