package kg.geoinfo.system.streamservice.client;

import kg.geoinfo.system.streamservice.dto.CameraDetailsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "geodata-service")
public interface GeoDataServiceClient {

    @GetMapping("/api/geodata/points/{id}")
    CameraDetailsDto getPointById(@PathVariable("id") UUID id);

}
