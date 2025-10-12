package kg.geoinfo.system.docservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.DocumentEvent;
import kg.geoinfo.system.docservice.dto.DocumentDto;
import kg.geoinfo.system.docservice.dto.PresignedUrlResponse;
import kg.geoinfo.system.docservice.dto.UpdateDocumentRequest;
import kg.geoinfo.system.docservice.mapper.DocumentMapper;
import kg.geoinfo.system.docservice.models.Document;
import kg.geoinfo.system.docservice.models.Tag;
import kg.geoinfo.system.docservice.repository.DocumentRepository;
import kg.geoinfo.system.docservice.repository.TagRepository;
import kg.geoinfo.system.docservice.service.filestore.FileStoreService;
import kg.geoinfo.system.docservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final TagRepository tagRepository;
    private final FileStoreService fileStoreService;
    private final DocumentMapper documentMapper;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    // private final WebClient.Builder webClientBuilder; // TODO: Uncomment when WebClient is configured

    // TODO: Implement robust cross-service access check.
    private void checkGeoObjectAccess(String currentUserEmail, UUID geoObjectId) {
        System.out.println("TODO: Implement access check for user " + currentUserEmail + " on geo-object: " + geoObjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsForGeoObject(String currentUserEmail, UUID geoObjectId) {
        checkGeoObjectAccess(currentUserEmail, geoObjectId);
        return documentMapper.toDto(documentRepository.findByGeoObjectId(geoObjectId));
    }

    @Override
    public DocumentDto uploadDocument(String currentUserEmail, UUID geoObjectId, String description, Set<String> tags, MultipartFile file) {
        checkGeoObjectAccess(currentUserEmail, geoObjectId);

        String fileKey = fileStoreService.save(file);

        Set<Tag> tagEntities = tags.stream()
                .map(tagName -> tagRepository.findByName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build())))
                .collect(Collectors.toSet());

        Document document = Document.builder()
                .geoObjectId(geoObjectId)
                .description(description)
                .fileName(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .mimeType(file.getContentType())
                .minioObjectKey(fileKey)
                .tags(tagEntities)
                .build();

        Document savedDocument = documentRepository.save(document);

        Map<String, Object> payload = objectMapper.convertValue(savedDocument, Map.class);
        payload.put("type", "document");
        kafkaProducerService.sendDocumentEvent(payload, DocumentEvent.EventType.CREATED);

        return documentMapper.toDto(savedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadDocument(String currentUserEmail, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        checkGeoObjectAccess(currentUserEmail, document.getGeoObjectId());

        return fileStoreService.find(document.getMinioObjectKey())
                .orElseThrow(() -> new RuntimeException("File not found in store"));
    }

    @Override
    public void deleteDocument(String currentUserEmail, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        checkGeoObjectAccess(currentUserEmail, document.getGeoObjectId());

        if (!document.getCreatedBy().equals(currentUserEmail)) {
            throw new AccessDeniedException("User is not the owner of the document");
        }

        fileStoreService.delete(document.getMinioObjectKey());
        documentRepository.delete(document);

        Map<String, Object> payload = Map.of("id", documentId, "type", "document");
        kafkaProducerService.sendDocumentEvent(payload, DocumentEvent.EventType.DELETED);
    }

    @Override
    public void deleteDocumentsByGeoObjectId(String currentUserEmail, UUID geoObjectId) {
        checkGeoObjectAccess(currentUserEmail, geoObjectId);
        List<Document> documents = documentRepository.findByGeoObjectId(geoObjectId);
        if (documents.isEmpty()) {
            return;
        }

        for (Document document : documents) {
            if (!document.getCreatedBy().equals(currentUserEmail)) {
                throw new AccessDeniedException("User is not the owner of all documents related to this geo-object");
            }
            fileStoreService.delete(document.getMinioObjectKey());
        }

        documentRepository.deleteAll(documents);

        for (Document document : documents) {
            Map<String, Object> payload = Map.of("id", document.getId(), "type", "document");
            kafkaProducerService.sendDocumentEvent(payload, DocumentEvent.EventType.DELETED);
        }
    }

    @Override
    public DocumentDto updateDocument(String currentUserEmail, UUID documentId, UpdateDocumentRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        checkGeoObjectAccess(currentUserEmail, document.getGeoObjectId());

        if (!document.getCreatedBy().equals(currentUserEmail)) {
            throw new AccessDeniedException("User is not the owner of the document");
        }

        Set<Tag> tagEntities = request.getTags().stream()
                .map(tagName -> tagRepository.findByName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build())))
                .collect(Collectors.toSet());

        document.setDescription(request.getDescription());
        document.setTags(tagEntities);

        Document updatedDocument = documentRepository.save(document);

        Map<String, Object> payload = objectMapper.convertValue(updatedDocument, Map.class);
        payload.put("type", "document");
        kafkaProducerService.sendDocumentEvent(payload, DocumentEvent.EventType.UPDATED);

        return documentMapper.toDto(updatedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlResponse generatePresignedUrl(String currentUserEmail, UUID documentId, long expiresInSeconds) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        checkGeoObjectAccess(currentUserEmail, document.getGeoObjectId());

        String fileKey = document.getMinioObjectKey();
        var url = fileStoreService.generatePresignedUrl(fileKey, expiresInSeconds);

        return new PresignedUrlResponse(url.toString(), expiresInSeconds);
    }
}