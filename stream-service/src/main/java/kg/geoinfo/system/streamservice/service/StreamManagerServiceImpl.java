package kg.geoinfo.system.streamservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import feign.FeignException;
import kg.geoinfo.system.streamservice.client.GeoDataServiceClient;
import kg.geoinfo.system.streamservice.client.MediaMtxClient;
import kg.geoinfo.system.streamservice.config.security.OAuth2ResourceOpaqueProperties;
import kg.geoinfo.system.streamservice.dto.CameraDetailsDto;
import kg.geoinfo.system.streamservice.dto.MediaMtxPathConfigDto;
import kg.geoinfo.system.streamservice.dto.StartStreamResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamManagerServiceImpl implements StreamManagerService {

    private final GeoDataServiceClient geoDataServiceClient;
    private final MediaMtxClient mediaMtxClient;
    private final RestTemplate restTemplate;
    private final OAuth2ResourceOpaqueProperties introspectionProperties;


    @Value("${mediamtx.hls.url}")
    private String webRtcBaseUrl;

    // DTO for introspection response
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenInfo(boolean active) {}

    @Override
    public StartStreamResponseDto startStream(UUID geoObjectId) {
        log.info("Received request to start stream for geo-object: {}", geoObjectId);
        String streamPath = geoObjectId.toString();

        try {
            // 1. Check if path already exists
            mediaMtxClient.getPath(streamPath);
            log.info("Stream path '{}' already exists in MediaMTX. Skipping creation.", streamPath);
        } catch (FeignException.NotFound e) {
            log.info("Stream path '{}' not found in MediaMTX. Proceeding to create.", streamPath);
            // Path does not exist, so we create it.
            // 2. Get camera details from geodata-service
            CameraDetailsDto cameraDetails = geoDataServiceClient.getPointById(geoObjectId);
            Map<String, Object> chars = cameraDetails.characteristics();
            Assert.notNull(chars, "Characteristics for camera cannot be null");

            // 3. Extract connection details
            String ip = (String) chars.get("ip_address");
            Object portObj = chars.get("port");
            String port = portObj != null ? String.valueOf(portObj) : null;
            String login = (String) chars.get("login");
            String password = (String) chars.get("password");

            Assert.hasText(ip, "Camera IP address is required");
            Assert.notNull(port, "Camera port is required");
            Assert.hasText(login, "Camera login is required");

            // 4. Construct RTSP URL
            String rtspUrl = String.format("rtsp://%s:%s@%s:%s/Streaming/Channels/101",
                    login, password, ip, port);
            log.info("Constructed RTSP URL: {}", rtspUrl);

            // 5. Call MediaMTX to add the path
            MediaMtxPathConfigDto config = new MediaMtxPathConfigDto(rtspUrl);
            try {
                mediaMtxClient.addPath(streamPath, config);
                log.info("Successfully registered stream path '{}' with MediaMTX", streamPath);
            } catch (Exception ex) {
                log.error("Failed to add path to MediaMTX for stream: {}", streamPath, ex);
                throw new RuntimeException("Could not start camera stream: " + ex.getMessage());
            }
        } catch (Exception e) {
            log.error("An unexpected error occurred while checking or creating stream path '{}'", streamPath, e);
            throw new RuntimeException("Could not start camera stream due to an unexpected error: " + e.getMessage());
        }


        // 6. Get current user's token
        String token = "";
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof BearerTokenAuthentication bearer) {
            token = bearer.getToken().getTokenValue();
        }

        log.info("WebRTC base URL: " + webRtcBaseUrl);

        // 7. Return the WebRTC URL to the client with token
        String webRtcUrl = UriComponentsBuilder.fromHttpUrl(webRtcBaseUrl)
                .pathSegment(streamPath)
                .queryParam("token", token)
                .toUriString();

        log.info("Created stream details: " + webRtcUrl);
        return new StartStreamResponseDto(webRtcUrl);
    }

    @Override
    public void stopStream(UUID geoObjectId) {
        String streamPath = geoObjectId.toString();
        log.info("Received request to stop stream for path: {}", streamPath);
        try {
            mediaMtxClient.deletePath(streamPath);
            log.info("Successfully unregistered stream path '{}' from MediaMTX", streamPath);
        } catch (Exception e) {
            log.error("Failed to delete path from MediaMTX for stream: {}", streamPath, e);
            // Don't rethrow, just log. The client has already closed the connection.
        }
    }

    @Override
    public boolean isStreamAccessAllowed(String query) {
        if (query == null || query.isBlank()) {
            log.warn("MediaMTX auth request without query string");
            return false;
        }

        // Extract token from "token=value"
        String token = null;
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("token=")) {
                token = param.substring(6);
                break;
            }
        }

        if (token == null) {
            log.warn("MediaMTX auth request query without 'token' parameter: {}", query);
            return false;
        }

        // Call introspection endpoint
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            TokenInfo tokenInfo = restTemplate.postForObject(introspectionProperties.getIntrospectionUri(), request, TokenInfo.class);

            if (tokenInfo != null && tokenInfo.active()) {
                log.debug("Token is active. Access granted.");
                return true;
            } else {
                log.warn("Token is inactive or invalid. Access denied.");
                return false;
            }
        } catch (Exception e) {
            log.error("Error during token introspection", e);
            return false;
        }
    }
}
