package kg.geoinfo.system.geoprintservice.repository;

import kg.geoinfo.system.geoprintservice.model.PrintTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PrintTaskRepository extends JpaRepository<PrintTask, UUID> {
}
