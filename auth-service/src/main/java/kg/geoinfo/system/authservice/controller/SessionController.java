package kg.geoinfo.system.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.geoinfo.system.authservice.dto.security.AuthorizedUser;
import kg.geoinfo.system.authservice.dto.security.AuthorizedUserDto;
import kg.geoinfo.system.authservice.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/security-session")
@Tag(name = "Контроллер управления контекстом авторизации пользователя")
public class SessionController {

    @GetMapping("/user")
    @Operation(description = "Получение информации об авторизованном пользователе")
    public AuthorizedUserDto getCurrentUser() {
        AuthorizedUser authorizedUser = SecurityUtils.getAuthUser();
        return AuthorizedUserDto.build(authorizedUser);
    }
}
