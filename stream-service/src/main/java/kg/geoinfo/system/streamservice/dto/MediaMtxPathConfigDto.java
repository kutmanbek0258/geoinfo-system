package kg.geoinfo.system.streamservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaMtxPathConfigDto(
        String source
) {
}
