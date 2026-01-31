package kg.geoinfo.system.streamservice.controller;

import kg.geoinfo.system.streamservice.dto.MediaMtxAuthRequest;
import kg.geoinfo.system.streamservice.dto.StartStreamRequestDto;
import kg.geoinfo.system.streamservice.dto.StartStreamResponseDto;
import kg.geoinfo.system.streamservice.service.StreamManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
@Slf4j
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

    @PostMapping("/auth")
    public ResponseEntity<Void> authenticate(@RequestBody MediaMtxAuthRequest request) {
        log.info("MediaMTX auth request for path: {}", request.getPath());

        // Передаем строку query из JSON в ваш метод
        boolean allowed = streamManagerService.isStreamAccessAllowed(request.getQuery());

        if (allowed) {
            return ResponseEntity.ok().build(); // Статус 200 - доступ разрешен
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // Статус 403 - отказ
        }
    }
}
