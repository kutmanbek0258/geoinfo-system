package kg.geoinfo.system.geoabstraction.service.client;

import kg.geoinfo.system.geoabstraction.dto.CommitAnalysisTaskRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;
import java.util.Map;

/**
 * Feign-клиент для взаимодействия с geodata-service: операции Commit и Rollback.
 */
@FeignClient(
        name = "geodata-service",
        url = "${feign.client.config.geodata-service.url}",
        configuration = FeignClientConfiguration.class
)
public interface GeoDataServiceClient {

    @PostMapping("/geodata/staging/{taskId}/commit")
    void commitTask(@PathVariable UUID taskId, @RequestBody CommitAnalysisTaskRequestDto dto);

    @DeleteMapping("/geodata/staging/{taskId}/rollback")
    void rollbackTask(@PathVariable UUID taskId);

    @GetMapping("/geodata/project-rasters/{id}")
    Map<String, Object> getProjectRasterById(@PathVariable("id") UUID id);
}
