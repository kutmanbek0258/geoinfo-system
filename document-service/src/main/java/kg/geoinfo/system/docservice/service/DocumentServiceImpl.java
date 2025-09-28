package kg.geoinfo.system.docservice.service;

import kg.geoinfo.system.docservice.dto.DocumentDto;
import kg.geoinfo.system.docservice.dto.UpdateDocumentRequest;
import kg.geoinfo.system.docservice.mapper.DocumentMapper;
import kg.geoinfo.system.docservice.models.Document;
import kg.geoinfo.system.docservice.models.Tag;
import kg.geoinfo.system.docservice.repository.DocumentRepository;
import kg.geoinfo.system.docservice.repository.TagRepository;
import kg.geoinfo.system.docservice.service.filestore.FileStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsForGeoObject(UUID geoObjectId) {
        return documentMapper.toDto(documentRepository.findByGeoObjectId(geoObjectId));
    }

    @Override
    public DocumentDto uploadDocument(UUID geoObjectId, String description, Set<String> tags, MultipartFile file) {
        // 1. Save file to file store
        String fileKey = fileStoreService.save(file);

        // 2. Handle tags
        Set<Tag> tagEntities = tags.stream()
                .map(tagName -> tagRepository.findByName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build())))
                .collect(Collectors.toSet());

        // 3. Create and save document metadata
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

        return documentMapper.toDto(savedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found")); // Replace with proper exception

        return fileStoreService.find(document.getMinioObjectKey())
                .orElseThrow(() -> new RuntimeException("File not found in store")); // Replace with proper exception
    }

    @Override
    public void deleteDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found")); // Replace with proper exception

        // Delete from file store first
        fileStoreService.delete(document.getMinioObjectKey());

        // Then delete metadata
        documentRepository.delete(document);
    }

    @Override
    public DocumentDto updateDocument(UUID documentId, UpdateDocumentRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found")); // Replace with proper exception

        // Handle tags
        Set<Tag> tagEntities = request.getTags().stream()
                .map(tagName -> tagRepository.findByName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build())))
                .collect(Collectors.toSet());

        document.setDescription(request.getDescription());
        document.setTags(tagEntities);

        Document updatedDocument = documentRepository.save(document);

        return documentMapper.toDto(updatedDocument);
    }
}
