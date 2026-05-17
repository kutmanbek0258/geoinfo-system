package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import kg.geoinfo.system.geoabstraction.dto.TerrainLayerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface GeoAbstractionService {
    GeoAbstractJobDto createJob(String name, MultipartFile file);
    GeoAbstractJobDto createSentinelJob(String name, MultipartFile file, List<String> channels, String indexType);
    GeoAbstractJobDto getJob(UUID jobId);
    Page<GeoAbstractJobDto> getJobs(Pageable pageable);
    Page<TerrainLayerDto> getLayers(Pageable pageable);
    void updateJobStatus(UUID jobId, String status, String errorMessage, Double minHeight, Double maxHeight, String terrainUrl);
    void deleteLayer(UUID id);
}
