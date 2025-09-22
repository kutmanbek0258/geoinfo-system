package kg.geoinfo.system.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    @JsonProperty(required = true)
    private UUID id;

    @JsonProperty(required = true)
    private String email;

    @JsonProperty(required = true)
    private String firstName;

    @JsonProperty(required = true)
    private String lastName;
    private String middleName;
    private LocalDate birthday;
    private UUID avatarFileId;
    private LocalDate registrationDate;
}
