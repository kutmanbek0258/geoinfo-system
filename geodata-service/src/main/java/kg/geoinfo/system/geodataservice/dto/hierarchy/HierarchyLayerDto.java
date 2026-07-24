package kg.geoinfo.system.geodataservice.dto.hierarchy;

import com.fasterxml.jackson.annotation.JsonInclude;
import kg.geoinfo.system.geodataservice.models.enums.LayerType;
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
public class HierarchyLayerDto {
    private UUID id;
    private String name;
    private LayerType type; // VECTOR / RASTER
    private UUID projectId;
    private List<HierarchyFolderDto> folders;
    private List<HierarchyObjectDto> objects;
}
