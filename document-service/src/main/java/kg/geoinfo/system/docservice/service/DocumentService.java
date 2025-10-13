package kg.geoinfo.system.docservice.service;

import kg.geoinfo.system.docservice.dto.DocumentDto;
import kg.geoinfo.system.docservice.dto.PresignedUrlResponse;
import kg.geoinfo.system.docservice.dto.UpdateDocumentRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DocumentService {

    List<DocumentDto> getDocumentsForGeoObject(String currentUserEmail, UUID geoObjectId);

    DocumentDto uploadDocument(String currentUserEmail, UUID geoObjectId, String description, Set<String> tags, MultipartFile file);

    byte[] downloadDocument(String currentUserEmail, UUID documentId);

    void deleteDocument(String currentUserEmail, UUID documentId);

    void deleteDocumentsByGeoObjectId(String currentUserEmail, UUID geoObjectId);

    DocumentDto updateDocument(String currentUserEmail, UUID documentId, UpdateDocumentRequest request);

    PresignedUrlResponse generatePresignedUrl(String currentUserEmail, UUID documentId, long expiresInSeconds);

    byte[] getPublicDocument(UUID documentId);
}
