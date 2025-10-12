package kg.geoinfo.system.docservice.controller;

import kg.geoinfo.system.docservice.dto.OnlyOfficeConfig;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import kg.geoinfo.system.docservice.service.OnlyOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class OnlyOfficeController {
    private final OnlyOfficeService onlyOfficeService;

    /**
     * Получить конфиг для OnlyOffice (режим edit/view)
     */
    @GetMapping("/{documentId}/onlyoffice-config")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')") // General read access
    public ResponseEntity<OnlyOfficeConfig> getOnlyOfficeConfig(
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, // Added
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "view") String mode
    ) {
        // The service now needs the user to perform access checks
        OnlyOfficeConfig config = onlyOfficeService.generateConfig(documentId, mode, principal.getName(), principal.getAttribute("name"));
        return ResponseEntity.ok(config);
    }

    /**
     * Callback от OnlyOffice (POST)
     */
    @PostMapping("/{documentId}/onlyoffice-callback")
    public ResponseEntity<Void> handleOnlyOfficeCallback(
            @PathVariable UUID documentId,
            @RequestBody OnlyOfficeCallback callback
    ) {
        onlyOfficeService.handleCallback(documentId, callback);
        return ResponseEntity.ok().build();
    }
}