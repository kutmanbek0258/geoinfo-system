package kg.geoinfo.system.geoprintservice.mapper;

import kg.geoinfo.system.geoprintservice.dto.PrintTaskDto;
import kg.geoinfo.system.geoprintservice.model.PrintTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PrintTaskMapper {
    @Mapping(target = "createdDate", expression = "java(task.getCreatedDate() != null ? task.getCreatedDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null)")
    @Mapping(target = "lastModifiedDate", expression = "java(task.getLastModifiedDate() != null ? task.getLastModifiedDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null)")
    PrintTaskDto toDto(PrintTask task);
}
