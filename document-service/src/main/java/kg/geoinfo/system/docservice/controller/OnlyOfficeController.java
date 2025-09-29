package kg.geoinfo.system.docservice.controller;

import kg.geoinfo.system.docservice.dto.OnlyOfficeConfig;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import kg.geoinfo.system.docservice.service.OnlyOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
//    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')") // актуализируйте права!
    public ResponseEntity<OnlyOfficeConfig> getOnlyOfficeConfig(
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "view") String mode,
            @RequestParam String userId,
            @RequestParam String userName
    ) {
        OnlyOfficeConfig config = onlyOfficeService.generateConfig(documentId, mode, userId, userName);
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