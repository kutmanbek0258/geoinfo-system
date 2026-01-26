package kg.geoinfo.system.streamservice.service;

import kg.geoinfo.system.streamservice.client.GeoDataServiceClient;
import kg.geoinfo.system.streamservice.client.MediaMtxClient;
import kg.geoinfo.system.streamservice.dto.CameraDetailsDto;
import kg.geoinfo.system.streamservice.dto.MediaMtxPathConfigDto;
import kg.geoinfo.system.streamservice.dto.StartStreamResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamManagerServiceImpl implements StreamManagerService {

    private final GeoDataServiceClient geoDataServiceClient;
    private final MediaMtxClient mediaMtxClient;

    @Value("${mediamtx.webrtc.url}")
    private String webRtcBaseUrl;

    @Override
    public StartStreamResponseDto startStream(UUID geoObjectId) {
        log.info("Received request to start stream for geo-object: {}", geoObjectId);

        // 1. Get camera details from geodata-service
        CameraDetailsDto cameraDetails = geoDataServiceClient.getPointById(geoObjectId);
        Map<String, Object> chars = cameraDetails.characteristics();
        Assert.notNull(chars, "Characteristics for camera cannot be null");

        // 2. Extract connection details
        String ip = (String) chars.get("ip_address");
        String port = (String) chars.get("port");
        String login = (String) chars.get("login");
        String password = (String) chars.get("password");

        Assert.hasText(ip, "Camera IP address is required");
        Assert.notNull(port, "Camera port is required");
        Assert.hasText(login, "Camera login is required");

        // 3. Construct RTSP URL
        String rtspUrl = String.format("rtsp://%s:%s@%s:%s/Streaming/Channels/101",
                login, password, ip, port);
        log.info("Constructed RTSP URL: {}", rtspUrl);

        // 4. Call MediaMTX to add the path
        String streamPath = geoObjectId.toString();
        MediaMtxPathConfigDto config = new MediaMtxPathConfigDto(rtspUrl);
        try {
            mediaMtxClient.addPath(streamPath, config);
            log.info("Successfully registered stream path '{}' with MediaMTX", streamPath);
        } catch (Exception e) {
            log.error("Failed to add path to MediaMTX for stream: {}", streamPath, e);
            throw new RuntimeException("Could not start camera stream: " + e.getMessage());
        }

        // 5. Return the WebRTC URL to the client
        String webRtcUrl = String.format("%s/%s", webRtcBaseUrl, streamPath);
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
}
