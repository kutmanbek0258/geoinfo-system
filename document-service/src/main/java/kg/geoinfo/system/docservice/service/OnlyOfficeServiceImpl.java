package kg.geoinfo.system.docservice.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OnlyOfficeServiceImpl implements OnlyOfficeService {
    private final DocumentRepository documentRepository;
    private final FileStoreService fileStoreService;
    private final ObjectMapper objectMapper;

    @Value("${onlyoffice.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${onlyoffice.doc-service-url}")
    private String docServiceUrl;

    @Value("${onlyoffice.internal-doc-server-url}")
    private String internalDocServerUrl;

    @Value("${onlyoffice.jwt-secret}")
    private String jwtSecret;

    private void checkGeoObjectAccess(String currentUserEmail, UUID geoObjectId) {
        log.warn("TODO: Implement access check for user {} on geo-object: {}", currentUserEmail, geoObjectId);
    }

    private static final Set<String> EDITABLE_FILE_TYPES = Set.of(
            "docx", "docm", "dotx", "dotm",
            "xlsx", "xlsb", "xlsm", "xltx", "xltm",
            "pptx", "ppsx", "potx", "pptm", "ppsm", "potm"
    );

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OnlyOfficeConfig generateConfig(UUID documentId, String userId, String userName) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        checkGeoObjectAccess(userId, doc.getGeoObjectId());

        String fileType = getFileTypeFromName(doc.getFileName());
        String mode = "view"; // Default to view mode
        if (EDITABLE_FILE_TYPES.contains(fileType.toLowerCase())) {
            // If the user is the owner, allow editing
            if (doc.getCreatedBy().equals(userId)) {
                mode = "edit";
            }
        }


        String fileUrl = docServiceUrl + "/api/documents/content/" + documentId;
        String key = doc.getId().toString() + "-" + doc.getLastModifiedDate().getTime();

        OnlyOfficeConfig.Document documentConfig = OnlyOfficeConfig.Document.builder()
                .title(doc.getFileName())
                .url(fileUrl)
                .fileType(fileType)
                .key(key)
                .build();

        OnlyOfficeConfig.EditorConfig editorConfig = OnlyOfficeConfig.EditorConfig.builder()
                .mode(mode)
                .callbackUrl(callbackBaseUrl + "/onlyoffice-callback/" + doc.getId())
                .userId(userId)
                .userName(userName)
                .build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("document", objectMapper.convertValue(documentConfig, Map.class));
        payload.put("editorConfig", objectMapper.convertValue(editorConfig, Map.class));
        log.info("editor config:" + editorConfig.toString());

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
        log.info("Received OnlyOffice callback for documentId: {} with status: {}", documentId, callbackPayload.getStatus());

        switch (callbackPayload.getStatus()) {
            case 2, 6: // Document is ready for saving (2) or must be force-saved (6)
                saveDocument(documentId, callbackPayload);
                break;
            case 1: // User is editing
                log.info("User is actively editing documentId: {}", documentId);
                break;
            case 4: // User closed the document without saving
                log.info("Document closed without changes for documentId: {}", documentId);
                break;
            case 3: // Error saving document
                log.error("OnlyOffice reported a save error for documentId: {}", documentId);
                break;
            case 7: // Error with force-saving
                log.error("OnlyOffice reported a force-save error for documentId: {}", documentId);
                break;
            default:
                log.warn("Received unknown OnlyOffice callback status: {} for documentId: {}", callbackPayload.getStatus(), documentId);
                break;
        }
    }

    private void saveDocument(UUID documentId, OnlyOfficeCallback callbackPayload) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found on callback: " + documentId));

        try {
            URL downloadUrl = new URL(callbackPayload.getUrl());

            // SSRF Protection: Validate the hostname
            URL trustedUrl = new URL(internalDocServerUrl);
            if (!downloadUrl.getHost().equals(trustedUrl.getHost())) {
                log.error("SSRF Attack Attempted! Callback URL {} does not match trusted host {}", downloadUrl, trustedUrl.getHost());
                throw new SecurityException("Callback URL is not trusted.");
            }

            log.info("Downloading updated document from trusted URL: {}", downloadUrl);
            try (InputStream is = downloadUrl.openStream()) {
                fileStoreService.overwrite(doc.getMinioObjectKey(), is);
                log.info("Successfully overwrote document in MinIO: {}", doc.getMinioObjectKey());
            }

        } catch (MalformedURLException e) {
            log.error("Invalid URL in OnlyOffice callback for documentId: {}. URL: {}", documentId, callbackPayload.getUrl(), e);
            throw new RuntimeException("Invalid download URL from OnlyOffice", e);
        } catch (IOException e) {
            log.error("Failed to download or save document from OnlyOffice for documentId: {}. URL: {}", documentId, callbackPayload.getUrl(), e);
            throw new RuntimeException("Could not download file from OnlyOffice", e);
        } catch (Exception e) {
            log.error("An unexpected error occurred during OnlyOffice document save for documentId: {}", documentId, e);
            throw new RuntimeException("Unexpected error during file overwrite", e);
        }
    }

    @Override
    public DocumentContent getDocumentContent(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        byte[] content = fileStoreService.find(doc.getMinioObjectKey())
                .orElseThrow(() -> new RuntimeException("File not found in store for key: " + doc.getMinioObjectKey()));
        return new DocumentContent(doc, content);
    }

    private String getFileTypeFromName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) return fileName.substring(lastDot + 1);
        return "";
    }
}
