package kg.geoinfo.system.docservice.service;

import kg.geoinfo.system.docservice.dto.DocumentDto;
import kg.geoinfo.system.docservice.dto.UpdateDocumentRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DocumentService {

    List<DocumentDto> getDocumentsForGeoObject(UUID geoObjectId);

    DocumentDto uploadDocument(UUID geoObjectId, String description, Set<String> tags, MultipartFile file);

    // Returns a resource to be downloaded - exact type depends on implementation
    // For now, returning byte[] for simplicity
    byte[] downloadDocument(UUID documentId);

    void deleteDocument(UUID documentId);

    DocumentDto updateDocument(UUID documentId, UpdateDocumentRequest request);

}
