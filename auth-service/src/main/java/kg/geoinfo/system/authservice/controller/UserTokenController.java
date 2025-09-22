package kg.geoinfo.system.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.geoinfo.system.authservice.dto.UserTokenInfoDto;
import kg.geoinfo.system.authservice.service.UserTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user-token")
@Tag(name = "Контроллер управления выданными токенами пользователя")
public class UserTokenController {

    private final UserTokenService userTokenService;

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('GET_OWN_TOKENS')")
    @Operation(description = "Получение списка выданных токенов текущего пользователя")
    public List<UserTokenInfoDto> searchUserTokens() {
        return userTokenService.getUserTokens();
    }

    @PostMapping("/recall")
    @PreAuthorize("hasAnyAuthority('RECALL_OWN_TOKENS')")
    @Operation(description = "Отозвать токен по его идентификатору")
    public void recallToken(@RequestParam String authorizationId) {
        userTokenService.recallToken(authorizationId);
    }
}
