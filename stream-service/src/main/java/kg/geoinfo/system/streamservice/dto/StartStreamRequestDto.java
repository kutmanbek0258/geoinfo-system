package kg.geoinfo.system.streamservice.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class StartStreamRequestDto {
    private UUID geoObjectId;
}
