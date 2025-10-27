package kg.geoinfo.system.docservice.controller;

import kg.geoinfo.system.docservice.config.security.filter.OnlyOfficeJwtFilter;
import kg.geoinfo.system.docservice.dto.DocumentContent;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import kg.geoinfo.system.docservice.dto.OnlyOfficeConfig;
import kg.geoinfo.system.docservice.models.Document;
import kg.geoinfo.system.docservice.service.OnlyOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
        OnlyOfficeConfig config = onlyOfficeService.generateConfig(documentId, mode, principal.getName(), principal.getAttribute("fullName"));
        return ResponseEntity.ok(config);
    }

    /**
     * Callback от OnlyOffice (POST)
     */
    @PostMapping("/onlyoffice-callback/{documentId}")
    public ResponseEntity<Map<String, Integer>> handleOnlyOfficeCallback(
            @PathVariable UUID documentId,
            @RequestAttribute(OnlyOfficeJwtFilter.ONLYOFFICE_CALLBACK_ATTRIBUTE) OnlyOfficeCallback callback) {
        onlyOfficeService.handleCallback(documentId, callback);
        // ONLYOFFICE expects a JSON response with an error code
        return ResponseEntity.ok(Map.of("error", 0));
    }

    /**
     * Эндпоинт для стриминга контента файла в OnlyOffice
     */
    @GetMapping("/content/{documentId}")
    public ResponseEntity<byte[]> getDocumentContent(@PathVariable UUID documentId) {
        DocumentContent documentContent = onlyOfficeService.getDocumentContent(documentId);
        Document document = documentContent.document();
        byte[] bytes = documentContent.content();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .body(bytes);
    }
}