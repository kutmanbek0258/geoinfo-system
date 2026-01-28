package kg.geoinfo.system.streamservice.service;

import kg.geoinfo.system.streamservice.dto.StartStreamResponseDto;

import java.util.UUID;

public interface StreamManagerService {
    StartStreamResponseDto startStream(UUID geoObjectId);
    void stopStream(UUID geoObjectId);
    boolean isStreamAccessAllowed(String query);
}
