package kg.geoinfo.system.geoprintservice.dto;

import kg.geoinfo.system.geoprintservice.enums.PrintTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PrintTaskDto extends AbstractDto<UUID> {
    private UUID id;
    private PrintTaskStatus status;
    private String layout;
    private String s3Url;
    private String errorMessage;
    private Map<String, Object> attributes;
}
