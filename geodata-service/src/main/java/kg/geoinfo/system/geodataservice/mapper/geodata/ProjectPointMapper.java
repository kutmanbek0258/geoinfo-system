package kg.geoinfo.system.geodataservice.mapper.geodata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectPointDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectPointDto;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.models.ProjectPoint;
import lombok.SneakyThrows;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring",
        uses = {ObjectMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class ProjectPointMapper {

    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "characteristics", target = "characteristics", qualifiedByName = "stringToMap")
    public abstract ProjectPointDto toDto(ProjectPoint projectPoint);

    @Mapping(target = "project", source = "projectId", qualifiedByName = "mapProject")
    @Mapping(source = "characteristics", target = "characteristics", qualifiedByName = "mapToString")
    public abstract ProjectPoint toEntity(CreateProjectPointDto createProjectPointDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "characteristics", target = "characteristics", qualifiedByName = "mapToString")
    public abstract void update(@MappingTarget ProjectPoint projectPoint, UpdateProjectPointDto updateProjectPointDto);

    @Named("mapProject")
    public Project mapProject(UUID id) {
        if (id == null) {
            return null;
        }
        Project project = new Project();
        project.setId(id);
        return project;
    }

    @SneakyThrows
    @Named("stringToMap")
    public Map<String, Object> stringToMap(String json) {
        if (json == null) {
            return null;
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    @SneakyThrows
    @Named("mapToString")
    public String mapToString(Map<String, Object> map) {
        if (map == null) return null;

        // Пробуем убрать лишние кавычки для String
        Map<String, Object> fixedMap = map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            Object v = e.getValue();
                            if (v instanceof String && ((String) v).startsWith("\"") && ((String) v).endsWith("\"")) {
                                return ((String) v).substring(1, ((String) v).length()-1);
                            }
                            return v;
                        }
                ));

        return objectMapper.writeValueAsString(fixedMap);
    }
}
