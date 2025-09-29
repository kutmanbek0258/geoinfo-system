package kg.geoinfo.system.docservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import kg.geoinfo.system.docservice.dto.OnlyOfficeConfig;
import kg.geoinfo.system.docservice.models.Document;
import kg.geoinfo.system.docservice.repository.DocumentRepository;
import kg.geoinfo.system.docservice.service.filestore.FileStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${onlyoffice.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${onlyoffice.jwt-secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OnlyOfficeConfig generateConfig(UUID documentId, String mode, String userId, String userName) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String presignedUrl = fileStoreService.generatePresignedUrl(doc.getMinioObjectKey(), 6000).toString();
        String fileType = getFileTypeFromName(doc.getFileName());
        String key = doc.getId().toString() + "-" + doc.getLastModifiedDate().getTime();

        OnlyOfficeConfig.Document documentConfig = OnlyOfficeConfig.Document.builder()
                .title(doc.getFileName())
                .url(presignedUrl)
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

    private String getFileTypeFromName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) return fileName.substring(lastDot + 1);
        return "";
    }
}
