package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.geoabstraction.config.MinioProperties;
import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import kg.geoinfo.system.geoabstraction.mapper.GeoAbstractMapper;
import kg.geoinfo.system.geoabstraction.models.GeoAbstractJob;
import kg.geoinfo.system.geoabstraction.models.enums.GeoAbstractJobStatus;
import kg.geoinfo.system.geoabstraction.repository.GeoAbstractJobRepository;
import kg.geoinfo.system.geoabstraction.service.client.GeoDataServiceClient;
import kg.geoinfo.system.geoabstraction.service.filestore.FileStoreService;
import kg.geoinfo.system.geoabstraction.service.kafka.KafkaProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAbstractionServiceImpl implements GeoAbstractionService {

    private final GeoAbstractJobRepository jobRepository;
    private final FileStoreService fileStoreService;
    private final KafkaProducerService kafkaProducerService;
    private final GeoAbstractMapper geoAbstractMapper;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public GeoAbstractJobDto createJob(String name, MultipartFile file, UUID projectId) {
        log.info("Creating terrain job with name {} for project {}", name, projectId);

        // 1. Save file to MinIO
        String objectKey = fileStoreService.save(file);

        // 2. Create job in DB
        GeoAbstractJob job = new GeoAbstractJob();
        job.setName(name);
        job.setProjectId(projectId);
        job.setStatus(GeoAbstractJobStatus.QUEUED);
        job.setTaskType("TERRAIN_MESH");
        job.setSourceBucket(minioProperties.getBucket());
        job.setSourceObjectKey(objectKey);
        job.setFileSize(file.getSize());
        job.setOutputBucket(minioProperties.getBucket());
        job.setOutputPrefix(job.getName() + "-" + UUID.randomUUID().toString().substring(0, 8));

        job = jobRepository.save(job);

        // 3. Send event to Kafka
        GeoAbstractJobEvent event = GeoAbstractJobEvent.builder()
                .jobId(job.getId())
                .projectId(job.getProjectId())
                .name(job.getName())
                .eventType(GeoAbstractJobEvent.EventType.QUEUED)
                .taskType(job.getTaskType())
                .sourceBucket(job.getSourceBucket())
                .sourceObjectKey(job.getSourceObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputPrefix(job.getOutputPrefix())
                .build();

        kafkaProducerService.sendGeoAbstractJobEvent(event);

        return geoAbstractMapper.toDto(job);
    }

    @Override
    public GeoAbstractJobDto createSentinelJob(String name, MultipartFile file, List<String> channels, String indexType, UUID projectId) {
        return this.createSatelliteJob(name, file, channels, indexType, "SENTINEL_COG", projectId);
    }

    @Override
    public GeoAbstractJobDto createLandsatJob(String name, MultipartFile file, List<String> channels, String indexType, UUID projectId) {
        return this.createSatelliteJob(name, file, channels, indexType, "LANDSAT_COG", projectId);
    }

    @Override
    @Transactional
    public GeoAbstractJobDto createRawGeoTiffJob(String name, MultipartFile file, UUID projectId) {
        return createSatelliteJob(name, file, null, null, "RAW_GEOTIFF_OPTIMIZE", projectId);
    }

    @Override
    @Transactional
    public GeoAbstractJobDto createTerrainJob(String name, MultipartFile file, UUID projectId) {
        return createJob(name, file, projectId);
    }

    private GeoAbstractJobDto createSatelliteJob(String name, MultipartFile file, List<String> channels, String indexType, String taskType, UUID projectId) {
        log.info("Creating {} job with name {}, channels {} and indexType {} for project {}", taskType, name, channels, indexType, projectId);

        // 1. Save file to MinIO
        String objectKey = fileStoreService.save(file);

        // 2. Create job in DB
        GeoAbstractJob job = new GeoAbstractJob();
        job.setName(name);
        job.setProjectId(projectId);
        job.setStatus(GeoAbstractJobStatus.QUEUED);
        job.setTaskType(taskType);

        Map<String, Object> characteristics = new HashMap<>();
        if (channels != null) {
            characteristics.put("channels", channels);
        }
        if (indexType != null) {
            characteristics.put("indexType", indexType);
        }
        job.setCharacteristics(characteristics);

        job.setSourceBucket(minioProperties.getBucket());
        job.setSourceObjectKey(objectKey);
        job.setFileSize(file.getSize());
        job.setOutputBucket(minioProperties.getBucket());
        job.setOutputPrefix(job.getName() + "-" + UUID.randomUUID().toString().substring(0, 8));

        job = jobRepository.save(job);

        // 3. Send event to Kafka
        GeoAbstractJobEvent event = GeoAbstractJobEvent.builder()
                .jobId(job.getId())
                .projectId(job.getProjectId())
                .name(job.getName())
                .eventType(GeoAbstractJobEvent.EventType.QUEUED)
                .taskType(job.getTaskType())
                .characteristics(job.getCharacteristics())
                .sourceBucket(job.getSourceBucket())
                .sourceObjectKey(job.getSourceObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputPrefix(job.getOutputPrefix())
                .build();

        kafkaProducerService.sendGeoAbstractJobEvent(event);

        return geoAbstractMapper.toDto(job);
    }

    @Override
    public String generateUploadUrl(String filename) {
        String extension = "";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            extension = filename.substring(i);
        }
        String objectKey = UUID.randomUUID().toString() + extension;
        String url = fileStoreService.generateUploadUrl(objectKey);
        
        if (url.contains("/" + minioProperties.getBucket() + "/")) {
            url = "/minio" + url.substring(url.indexOf("/" + minioProperties.getBucket() + "/"));
        }
        
        return url + "###" + objectKey;
    }

    @Override
    @Transactional
    public GeoAbstractJobDto createJobConfirm(String name, String objectKey, Long fileSize, String taskType, List<String> channels, String indexType, UUID projectId) {
        log.info("Confirming job creation for {} with objectKey {} and taskType {} for project {}", name, objectKey, taskType, projectId);
        
        if (!fileStoreService.exists(objectKey)) {
            throw new RuntimeException("File does not exist in store: " + objectKey);
        }

        GeoAbstractJob job = new GeoAbstractJob();
        job.setName(name);
        job.setProjectId(projectId);
        job.setStatus(GeoAbstractJobStatus.QUEUED);
        job.setTaskType(taskType);
        job.setSourceBucket(minioProperties.getBucket());
        job.setSourceObjectKey(objectKey);
        job.setFileSize(fileSize);
        job.setOutputBucket(minioProperties.getBucket());
        job.setOutputPrefix(job.getName() + "-" + UUID.randomUUID().toString().substring(0, 8));

        Map<String, Object> characteristics = new HashMap<>();
        if (channels != null) {
            characteristics.put("channels", channels);
        }
        if (indexType != null) {
            characteristics.put("indexType", indexType);
        }
        job.setCharacteristics(characteristics);

        job = jobRepository.save(job);

        GeoAbstractJobEvent event = GeoAbstractJobEvent.builder()
                .jobId(job.getId())
                .projectId(job.getProjectId())
                .name(job.getName())
                .eventType(GeoAbstractJobEvent.EventType.QUEUED)
                .taskType(job.getTaskType())
                .characteristics(job.getCharacteristics())
                .sourceBucket(job.getSourceBucket())
                .sourceObjectKey(job.getSourceObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputPrefix(job.getOutputPrefix())
                .build();

        kafkaProducerService.sendGeoAbstractJobEvent(event);

        return geoAbstractMapper.toDto(job);
    }

    @Override
    @Transactional
    public GeoAbstractJobDto createJobVerify(String name, String objectKey, Long fileSize, String dataType, UUID projectId) {
        log.info("Creating verification job for {} with objectKey {} and dataType {} for project {}", name, objectKey, dataType, projectId);
        
        if (!fileStoreService.exists(objectKey)) {
            throw new RuntimeException("File does not exist in store: " + objectKey);
        }

        GeoAbstractJob job = new GeoAbstractJob();
        job.setName(name);
        job.setProjectId(projectId);
        job.setStatus(GeoAbstractJobStatus.VERIFYING);
        job.setTaskType("VERIFY_FILE");
        job.setSourceBucket(minioProperties.getBucket());
        job.setSourceObjectKey(objectKey);
        job.setFileSize(fileSize);
        job.setOutputBucket(minioProperties.getBucket());
        job.setOutputPrefix(job.getName() + "-" + UUID.randomUUID().toString().substring(0, 8));

        Map<String, Object> characteristics = new HashMap<>();
        characteristics.put("dataType", dataType);
        job.setCharacteristics(characteristics);

        job = jobRepository.save(job);

        GeoAbstractJobEvent event = GeoAbstractJobEvent.builder()
                .jobId(job.getId())
                .projectId(job.getProjectId())
                .name(job.getName())
                .eventType(GeoAbstractJobEvent.EventType.QUEUED)
                .taskType(job.getTaskType())
                .characteristics(job.getCharacteristics())
                .sourceBucket(job.getSourceBucket())
                .sourceObjectKey(job.getSourceObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputPrefix(job.getOutputPrefix())
                .build();

        kafkaProducerService.sendGeoAbstractJobEvent(event);

        return geoAbstractMapper.toDto(job);
    }

    @Override
    @Transactional
    public GeoAbstractJobDto startImport(UUID jobId, Map<String, Object> params) {
        log.info("Starting import for job {} with params {}", jobId, params);

        GeoAbstractJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        if (job.getStatus() != GeoAbstractJobStatus.VERIFIED) {
            throw new RuntimeException("Job must be in VERIFIED status to start import. Current status: " + job.getStatus());
        }

        String taskType = (String) params.get("taskType");
        if (taskType == null || taskType.trim().isEmpty()) {
            throw new RuntimeException("taskType is required for starting import");
        }

        job.setStatus(GeoAbstractJobStatus.QUEUED);
        job.setTaskType(taskType);
        
        // Merge import parameters
        if (job.getCharacteristics() == null) {
            job.setCharacteristics(new HashMap<>());
        }
        job.getCharacteristics().putAll(params);

        job = jobRepository.save(job);

        GeoAbstractJobEvent event = GeoAbstractJobEvent.builder()
                .jobId(job.getId())
                .projectId(job.getProjectId())
                .name(job.getName())
                .eventType(GeoAbstractJobEvent.EventType.QUEUED)
                .taskType(job.getTaskType())
                .characteristics(job.getCharacteristics())
                .sourceBucket(job.getSourceBucket())
                .sourceObjectKey(job.getSourceObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputPrefix(job.getOutputPrefix())
                .build();

        kafkaProducerService.sendGeoAbstractJobEvent(event);

        return geoAbstractMapper.toDto(job);
    }

    @Override
    public GeoAbstractJobDto getJob(UUID jobId) {
        GeoAbstractJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        return geoAbstractMapper.toDto(job);
    }

    @Override
    public Page<GeoAbstractJobDto> getJobs(Pageable pageable, UUID projectId) {
        return jobRepository.findByProjectId(projectId, pageable)
                .map(geoAbstractMapper::toDto);
    }

    @Override
    @Transactional
    public void updateJobStatus(UUID jobId, String status, String errorMessage, Double minHeight, Double maxHeight, String terrainUrl, String cogObjectKey, String taskType, MultiPolygon bbox, Map<String, Object> characteristics) {
        log.info("Updating job {} status to {} (Task: {})", jobId, status, taskType);

        GeoAbstractJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        if (job.getStatus() == GeoAbstractJobStatus.READY && !"TERRAIN_COG".equals(taskType)) {
            log.info("Job {} is already READY, ignoring update to {}", jobId, status);
            return;
        }

        if ("VERIFY_FILE".equals(taskType)) {
            if ("READY".equals(status)) {
                job.setStatus(GeoAbstractJobStatus.VERIFIED);
                job.setBbox(bbox);
                if (characteristics != null) {
                    if (job.getCharacteristics() == null) {
                        job.setCharacteristics(new HashMap<>());
                    }
                    job.getCharacteristics().putAll(characteristics);
                }
            } else if ("FAILED".equals(status)) {
                job.setStatus(GeoAbstractJobStatus.FAILED);
                job.setErrorMessage(errorMessage);
            }
            jobRepository.save(job);
            return;
        }

        if (terrainUrl != null && terrainUrl.endsWith("/layer.json")) {
            terrainUrl = terrainUrl.substring(0, terrainUrl.length() - 10);
        } else if (terrainUrl != null && terrainUrl.endsWith("/layer.json/")) {
            terrainUrl = terrainUrl.substring(0, terrainUrl.length() - 11);
        }

        GeoAbstractJobStatus jobStatus = GeoAbstractJobStatus.valueOf(status);
        job.setStatus(jobStatus);
        job.setErrorMessage(errorMessage);
        job.setMinHeight(minHeight);
        job.setMaxHeight(maxHeight);
        job.setBbox(bbox);
        jobRepository.save(job);

        if ("TERRAIN_COG".equals(taskType) && "READY".equals(status)) {
            Map<String, Object> terrainPayload = new HashMap<>();
            terrainPayload.put("projectId", job.getProjectId());
            terrainPayload.put("jobId", job.getId());
            terrainPayload.put("outputPrefix", job.getOutputPrefix());
            terrainPayload.put("name", job.getName());
            terrainPayload.put("terrainUrl", "/terrain/" + job.getOutputPrefix() + "/");
            terrainPayload.put("cogObjectKey", cogObjectKey);
            terrainPayload.put("status", "READY");
            kafkaProducerService.sendTerrainProcessedEvent(terrainPayload);
            return;
        }

        if (("3D_TILES".equals(taskType) || "CITYGML".equals(taskType)) && "READY".equals(status)) {
            Map<String, Object> tilesPayload = new HashMap<>();
            tilesPayload.put("projectId", job.getProjectId());
            tilesPayload.put("jobId", job.getId());
            tilesPayload.put("outputPrefix", job.getOutputPrefix());
            tilesPayload.put("name", job.getName());
            tilesPayload.put("tilesetUrl", "/3dtiles/" + job.getOutputPrefix() + "/tileset.json");
            tilesPayload.put("sourceObjectKey", job.getSourceObjectKey());
            tilesPayload.put("taskType", taskType);
            tilesPayload.put("status", "READY");
            kafkaProducerService.sendTerrainProcessedEvent(tilesPayload);
            return;
        }

        if (jobStatus == GeoAbstractJobStatus.READY && "TERRAIN_MESH".equals(job.getTaskType())) {

            if (cogObjectKey == null) {
                log.info("Triggering COG optimization for terrain job {}", jobId);
                GeoAbstractJobEvent cogEvent = GeoAbstractJobEvent.builder()
                        .jobId(job.getId())
                        .name(job.getName() + " COG")
                        .eventType(GeoAbstractJobEvent.EventType.QUEUED)
                        .taskType("TERRAIN_COG")
                        .sourceBucket(job.getSourceBucket())
                        .sourceObjectKey(job.getSourceObjectKey())
                        .outputBucket(job.getOutputBucket())
                        .outputPrefix("terrain-cog/" + job.getId())
                        .build();

                kafkaProducerService.sendGeoAbstractJobEvent(cogEvent);
            }
        }

        if (jobStatus == GeoAbstractJobStatus.READY && 
           ("SENTINEL_COG".equals(job.getTaskType()) || "LANDSAT_COG".equals(job.getTaskType()) || "NETCDF_COG".equals(job.getTaskType()) || "RAW_GEOTIFF_OPTIMIZE".equals(job.getTaskType()))) {
            
            String layerName = job.getOutputPrefix();
            
            // Determine style
            String styleName = "raster"; // default
            if ("NETCDF_COG".equals(job.getTaskType())) {
                styleName = "environmental_spectral";
            } else if (job.getCharacteristics() != null) {
                String indexType = (String) job.getCharacteristics().get("indexType");
                if (indexType != null) {
                    switch (indexType.toUpperCase()) {
                        case "NDVI", "SAVI", "EVI" -> styleName = "vegetation_index";
                        case "GNDVI" -> styleName = "gndvi";
                        case "NDWI" -> styleName = "ndwi_water";
                        case "NDMI" -> styleName = "ndmi_moisture";
                        case "NBR" -> styleName = "nbr_burn";
                        case "NDSI" -> styleName = "ndsi_snow";
                        case "NDBI" -> styleName = "ndbi_urban";
                    }
                }
            }

            Map<String, Object> rasterPayload = new HashMap<>();
            UUID projectId = job.getProjectId();
            rasterPayload.put("id", job.getId().toString());
            rasterPayload.put("projectId", projectId != null ? projectId.toString() : null);
            rasterPayload.put("name", job.getName());
            rasterPayload.put("description", "Published raster layer from job " + job.getId());
            rasterPayload.put("cogObjectKey", cogObjectKey);
            rasterPayload.put("bbox", bbox != null ? bbox.toText() : null);
            rasterPayload.put("crs", job.getCrs() != null ? job.getCrs() : "EPSG:4326");
            rasterPayload.put("colormapId", styleName);
            rasterPayload.put("resampling", "bilinear");
            rasterPayload.put("dateCaptured", new java.util.Date().getTime());
            rasterPayload.put("status", "ACTIVE");
            rasterPayload.put("characteristics", job.getCharacteristics());
            kafkaProducerService.sendRasterProcessedEvent(rasterPayload);
        }

        if (jobStatus == GeoAbstractJobStatus.READY || jobStatus == GeoAbstractJobStatus.FAILED) {
            if ("TERRAIN_MESH".equals(job.getTaskType()) && jobStatus == GeoAbstractJobStatus.READY) {
                log.info("Keeping source file for job {} to allow TERRAIN_COG processing", jobId);
            } else {
                try {
                    if (job.getSourceObjectKey() != null) {
                        log.info("Cleaning up source file {} for job {} (Status: {})", 
                                job.getSourceObjectKey(), jobId, jobStatus);
                        fileStoreService.delete(job.getSourceObjectKey());
                    }
                } catch (Exception e) {
                    log.error("Failed to cleanup source file for job {}: {}", jobId, e.getMessage());
                }
            }
        }
    }
}
