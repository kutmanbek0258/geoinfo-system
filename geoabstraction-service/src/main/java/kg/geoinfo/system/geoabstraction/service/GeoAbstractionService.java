package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.List;
import java.util.UUID;

public interface GeoAbstractionService {
    GeoAbstractJobDto createJob(String name, MultipartFile file, UUID projectId);
    GeoAbstractJobDto createSentinelJob(String name, MultipartFile file, List<String> channels, String indexType, UUID projectId);
    GeoAbstractJobDto createLandsatJob(String name, MultipartFile file, List<String> channels, String indexType, UUID projectId);
    GeoAbstractJobDto createRawGeoTiffJob(String name, MultipartFile file, UUID projectId);
    GeoAbstractJobDto createTerrainJob(String name, MultipartFile file, UUID projectId);
    
    // Direct Upload support
    String generateUploadUrl(String filename);
    GeoAbstractJobDto createJobConfirm(String name, String objectKey, Long fileSize, String taskType, List<String> channels, String indexType, UUID projectId);
    
    // Two-step upload verification & import
    GeoAbstractJobDto createJobVerify(String name, String objectKey, Long fileSize, String dataType, UUID projectId);
    GeoAbstractJobDto startImport(UUID jobId, java.util.Map<String, Object> params);

    GeoAbstractJobDto getJob(UUID jobId);
    Page<GeoAbstractJobDto> getJobs(Pageable pageable, UUID projectId);
    void updateJobStatus(UUID jobId, String status, String errorMessage, Double minHeight, Double maxHeight, String terrainUrl, String cogObjectKey, String taskType, MultiPolygon bbox, java.util.Map<String, Object> characteristics);
}
