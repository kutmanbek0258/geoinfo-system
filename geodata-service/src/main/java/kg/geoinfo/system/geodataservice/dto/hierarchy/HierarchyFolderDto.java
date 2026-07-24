package kg.geoinfo.system.geodataservice.dto.hierarchy;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HierarchyFolderDto {
    private UUID id;
    private String name;
    private String description;
    private UUID layerId;
    private UUID parentId;
    private UUID projectId;
    private Map<String, Object> characteristics;
    private List<HierarchyFolderDto> subfolders;
    private List<HierarchyObjectDto> objects;
}
