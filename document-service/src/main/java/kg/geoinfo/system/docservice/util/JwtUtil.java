package kg.geoinfo.system.docservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecretKey getSigningKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public OnlyOfficeCallback parseCallbackToken(String token, String secret) {
        try {
            SecretKey key = getSigningKey(secret);

            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            Claims body = jws.getBody();

            // ONLYOFFICE nests the callback data inside a "payload" claim
            Map<String, Object> payloadMap = body.get("payload", Map.class);
            if (payloadMap == null) {
                // Or it might be at the top level, depending on version/configuration
                payloadMap = body;
            }

            return objectMapper.convertValue(payloadMap, OnlyOfficeCallback.class);
        } catch (Exception e) {
            // Log the exception in a real scenario
            System.err.println("JWT parsing failed: " + e.getMessage());
            throw new SecurityException("Invalid JWT token for OnlyOffice callback", e);
        }
    }
}
