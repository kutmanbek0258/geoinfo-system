package kg.geoinfo.system.geoabstraction.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class RasterStyleDto extends AbstractDto<UUID> {
    private UUID id;
    private String name;
    private String title;
    private String type;
    private List<Map<String, Object>> config;
    private boolean isSystem;
}
