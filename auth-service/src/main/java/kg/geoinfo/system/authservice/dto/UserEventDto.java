package kg.geoinfo.system.authservice.dto;

import kg.geoinfo.system.authservice.dao.type.UserEventType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventDto {

    private UUID id;
    private UserEventType eventType;
    private String eventTypeName;
    private String ipAddress;
    private String clientId;
    private String browser;
    private String device;
    private String os;
    private LocalDateTime creationDate;
}
