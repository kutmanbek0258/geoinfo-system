package kg.geoinfo.system.geodataservice.repository;

import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectMultilineRepository extends JpaRepository<ProjectMultiline, UUID> {
    Page<ProjectMultiline> findAllByProjectId(Pageable pageable, UUID projectId);
}
