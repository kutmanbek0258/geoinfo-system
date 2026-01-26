package kg.geoinfo.system.streamservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CameraDetailsDto(
        UUID id,
        Map<String, Object> characteristics
) {
}
