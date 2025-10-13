package kg.geoinfo.system.docservice.controller;

import kg.geoinfo.system.docservice.dto.DocumentDto;
import kg.geoinfo.system.docservice.dto.PresignedUrlResponse;
import kg.geoinfo.system.docservice.dto.UpdateDocumentRequest;
import kg.geoinfo.system.docservice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/geo/{geoObjectId}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<List<DocumentDto>> getDocumentsForGeoObject(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID geoObjectId) {
        return ResponseEntity.ok(documentService.getDocumentsForGeoObject(principal.getName(), geoObjectId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_CREATE')")
    public ResponseEntity<DocumentDto> uploadDocument(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal,
                                                      @RequestParam("geoObjectId") UUID geoObjectId,
                                                      @RequestParam("description") String description,
                                                      @RequestParam("tags") Set<String> tags,
                                                      @RequestParam("file") MultipartFile file) {
        DocumentDto uploadedDocument = documentService.uploadDocument(principal.getName(), geoObjectId, description, tags, file);
        return new ResponseEntity<>(uploadedDocument, HttpStatus.CREATED);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<byte[]> downloadDocument(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID documentId) {
        byte[] data = documentService.downloadDocument(principal.getName(), documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=file");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENT_DELETE')")
    public void deleteDocument(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID documentId) {
        documentService.deleteDocument(principal.getName(), documentId);
    }

    @PutMapping("/{documentId}")
    @PreAuthorize("hasAuthority('DOCUMENT_UPDATE')")
    public ResponseEntity<DocumentDto> updateDocument(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, @PathVariable UUID documentId, @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.updateDocument(principal.getName(), documentId, request));
    }

    @GetMapping("/{documentId}/presigned-url")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal,
                                                              @PathVariable UUID documentId,
                                                              @RequestParam(value = "expiresInSeconds", defaultValue = "300") long expiresInSeconds) {
        return ResponseEntity.ok(documentService.generatePresignedUrl(principal.getName(), documentId, expiresInSeconds));
    }

    @GetMapping("/public/image/{documentId}")
    public ResponseEntity<byte[]> getPublicImage(@PathVariable UUID documentId) {
        byte[] data = documentService.getPublicDocument(documentId);
        HttpHeaders headers = new HttpHeaders();
        // Здесь можно добавить Content-Type, если он известен
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
