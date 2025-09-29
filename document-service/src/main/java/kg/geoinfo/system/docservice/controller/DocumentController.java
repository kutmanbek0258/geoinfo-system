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
    public ResponseEntity<List<DocumentDto>> getDocumentsForGeoObject(@PathVariable UUID geoObjectId) {
        return ResponseEntity.ok(documentService.getDocumentsForGeoObject(geoObjectId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> uploadDocument(@RequestParam("geoObjectId") UUID geoObjectId,
                                                      @RequestParam("description") String description,
                                                      @RequestParam("tags") Set<String> tags,
                                                      @RequestParam("file") MultipartFile file) {
        DocumentDto uploadedDocument = documentService.uploadDocument(geoObjectId, description, tags, file);
        return new ResponseEntity<>(uploadedDocument, HttpStatus.CREATED);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID documentId) {
        // This is a simplified download implementation. A real implementation would be more robust.
        byte[] data = documentService.downloadDocument(documentId);
        // Ideally, we should also fetch the document's metadata to set the correct content-type and filename.
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=file"); // Placeholder filename
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId);
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<DocumentDto> updateDocument(@PathVariable UUID documentId, @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.updateDocument(documentId, request));
    }

    /**
     * Generate a presigned URL for downloading/viewing a document.
     * @param documentId UUID документа
     * @param expiresInSeconds срок жизни ссылки (сек), по умолчанию 300
     */
    @GetMapping("/{documentId}/presigned-url")
//    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')") // актуализируйте права
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @PathVariable UUID documentId,
            @RequestParam(value = "expiresInSeconds", defaultValue = "300") long expiresInSeconds
    ) {
        return ResponseEntity.ok(documentService.generatePresignedUrl(documentId, expiresInSeconds));
    }
}
