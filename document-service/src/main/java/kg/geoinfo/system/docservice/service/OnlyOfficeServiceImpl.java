package kg.geoinfo.system.docservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import kg.geoinfo.system.docservice.dto.DocumentContent;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import kg.geoinfo.system.docservice.dto.OnlyOfficeConfig;
import kg.geoinfo.system.docservice.models.Document;
import kg.geoinfo.system.docservice.repository.DocumentRepository;
import kg.geoinfo.system.docservice.service.filestore.FileStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OnlyOfficeServiceImpl implements OnlyOfficeService {
    private final DocumentRepository documentRepository;
    private final FileStoreService fileStoreService;
    private final ObjectMapper objectMapper;
    // private final WebClient.Builder webClientBuilder; // TODO: Uncomment when WebClient is configured

    @Value("${onlyoffice.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${onlyoffice.doc-service-url}")
    private String docServiceUrl;

    @Value("${onlyoffice.jwt-secret}")
    private String jwtSecret;

    // TODO: This is a duplicate of the method in DocumentServiceImpl. Refactor to a shared service.
    private void checkGeoObjectAccess(String currentUserEmail, UUID geoObjectId) {
        System.out.println("TODO: Implement access check for user " + currentUserEmail + " on geo-object: " + geoObjectId);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OnlyOfficeConfig generateConfig(UUID documentId, String mode, String userId, String userName) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Perform access check
        checkGeoObjectAccess(userId, doc.getGeoObjectId());

        // Additional check for edit mode
        if ("edit".equalsIgnoreCase(mode) && !doc.getCreatedBy().equals(userId)) {
            // In a real scenario, you would check for 'DOCUMENT_EDIT_ONLINE' authority as well.
            throw new AccessDeniedException("User is not the owner and cannot edit the document.");
        }

        String fileUrl = docServiceUrl + "/api/documents/" + documentId + "/content";
        String fileType = getFileTypeFromName(doc.getFileName());
        String key = doc.getId().toString() + "-" + doc.getLastModifiedDate().getTime();

        OnlyOfficeConfig.Document documentConfig = OnlyOfficeConfig.Document.builder()
                .title(doc.getFileName())
                .url(fileUrl)
                .fileType(fileType)
                .key(key)
                .build();

        OnlyOfficeConfig.EditorConfig editorConfig = OnlyOfficeConfig.EditorConfig.builder()
                .mode(mode)
                .callbackUrl(callbackBaseUrl + "/" + doc.getId() + "/onlyoffice-callback")
                .userId(userId)
                .userName(userName)
                .build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("document", objectMapper.convertValue(documentConfig, Map.class));
        payload.put("editorConfig", objectMapper.convertValue(editorConfig, Map.class));

        String token = Jwts.builder()
                .setClaims(payload)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();

        return OnlyOfficeConfig.builder()
                .document(documentConfig)
                .editorConfig(editorConfig)
                .token(token)
                .build();
    }

    @Override
    public void handleCallback(UUID documentId, OnlyOfficeCallback callbackPayload) {
        if (callbackPayload.getStatus() == 2 || callbackPayload.getStatus() == 6) {
            Document doc = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
            String minioKey = doc.getMinioObjectKey();
            try (InputStream is = new URL(callbackPayload.getUrl()).openStream()) {
                fileStoreService.overwrite(minioKey, is);
            } catch (Exception e) {
                throw new RuntimeException("OnlyOffice callback file overwrite failed", e);
            }
        }
    }

    @Override
    public DocumentContent getDocumentContent(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        byte[] content = fileStoreService.find(doc.getMinioObjectKey())
                .orElseThrow(() -> new RuntimeException("File not found in store"));
        return new DocumentContent(doc, content);
    }

    private String getFileTypeFromName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) return fileName.substring(lastDot + 1);
        return "";
    }
}
