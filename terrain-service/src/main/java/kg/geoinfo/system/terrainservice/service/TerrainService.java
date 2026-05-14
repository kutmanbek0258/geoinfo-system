package kg.geoinfo.system.terrainservice.service;

import kg.geoinfo.system.terrainservice.dto.TerrainJobDto;
import kg.geoinfo.system.terrainservice.dto.TerrainLayerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface TerrainService {
    TerrainJobDto createJob(String name, MultipartFile file);
    TerrainJobDto getJob(UUID jobId);
    Page<TerrainJobDto> getJobs(Pageable pageable);
    Page<TerrainLayerDto> getLayers(Pageable pageable);
    void updateJobStatus(UUID jobId, String status, String errorMessage, Double minHeight, Double maxHeight, String terrainUrl);
    void deleteLayer(UUID id);
}
