package kg.geoinfo.system.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.geoinfo.system.authservice.service.ResourceServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/resource")
@Tag(name = "Контроллер ресурс сервера SSO")
public class ResourceServerController {

    private final ResourceServerService resourceServerService;

    @GetMapping(value = "/user/{userId}/avatar")
    @PreAuthorize("hasAuthority('SCOPE_SSO.USER_AVATAR')")
    @Operation(description = "Получение аватарки пользователя")
    public ResponseEntity<byte[]> downloadUserAvatar(@PathVariable("userId") UUID userId) {
        return resourceServerService.getUserAvatar(userId);
    }
}
