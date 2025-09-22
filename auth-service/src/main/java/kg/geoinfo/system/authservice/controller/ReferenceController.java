package kg.geoinfo.system.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.geoinfo.system.authservice.dto.ReferenceDto;
import kg.geoinfo.system.authservice.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reference")
@Tag(name = "Контроллер получения справочников")
public class ReferenceController {

    private final ReferenceService referenceService;

    @GetMapping("/auth-methods")
    @Operation(description = "Получения методов авторизации OAuth2 клиентов")
    public List<ReferenceDto<String>> getAuthMethods() {
        return referenceService.getAuthMethods();
    }

    @GetMapping("/grant-types")
    @Operation(description = "Получение доступных grant type OAuth2 клиентов")
    public List<ReferenceDto<String>> getGrantTypes() {
        return referenceService.getGrantTypes();
    }

    @GetMapping("/scopes")
    @Operation(description = "Получение доступных scopes OAuth2 клиентов")
    public List<ReferenceDto<String>> getScopes() {
        return referenceService.getScopes();
    }
}
