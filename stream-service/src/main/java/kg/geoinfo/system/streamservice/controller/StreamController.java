package kg.geoinfo.system.streamservice.controller;

import kg.geoinfo.system.streamservice.dto.StartStreamRequestDto;
import kg.geoinfo.system.streamservice.dto.StartStreamResponseDto;
import kg.geoinfo.system.streamservice.service.StreamManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
public class StreamController {

    private final StreamManagerService streamManagerService;

    @PostMapping("/start")
    public ResponseEntity<StartStreamResponseDto> startStream(@RequestBody StartStreamRequestDto request) {
        StartStreamResponseDto response = streamManagerService.startStream(request.getGeoObjectId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopStream(@RequestBody StartStreamRequestDto request) {
        streamManagerService.stopStream(request.getGeoObjectId());
        return ResponseEntity.ok().build();
    }
}
