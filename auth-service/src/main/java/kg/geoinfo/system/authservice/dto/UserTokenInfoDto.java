package kg.geoinfo.system.authservice.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTokenInfoDto {

    private String authorizationId;
    private LocalDateTime startDate;
    private LocalDateTime lastRefreshDate;
    private String clientId;
    private String clientName;
    private List<String> scopeNames;
    private String grantTypeName;
    private String clientRedirectUri;
}
