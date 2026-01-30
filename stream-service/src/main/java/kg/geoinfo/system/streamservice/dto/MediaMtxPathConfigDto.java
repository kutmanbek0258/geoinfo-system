package kg.geoinfo.system.streamservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaMtxPathConfigDto {
    private String source;
    private boolean sourceOnDemand;
    private String sourceOnDemandCloseAfter;
}
