package kg.geoinfo.system.geodataservice.dto.hierarchy;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectHierarchyDto {
    private UUID projectId;
    private String projectName;
    private List<HierarchyLayerDto> layers;
}
