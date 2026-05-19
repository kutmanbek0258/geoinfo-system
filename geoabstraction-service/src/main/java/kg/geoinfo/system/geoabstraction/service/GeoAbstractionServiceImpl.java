package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.common.GeoAbstractJobEvent;
import kg.geoinfo.system.geoabstraction.config.GeoServerProperties;
import kg.geoinfo.system.geoabstraction.config.MinioProperties;
import kg.geoinfo.system.geoabstraction.dto.GeoAbstractJobDto;
import kg.geoinfo.system.geoabstraction.dto.TerrainLayerDto;
import kg.geoinfo.system.geoabstraction.mapper.TerrainLayerMapper;
import kg.geoinfo.system.geoabstraction.mapper.GeoAbstractMapper;
import kg.geoinfo.system.geoabstraction.models.GeoAbstractJob;
import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import kg.geoinfo.system.geoabstraction.models.enums.GeoAbstractJobStatus;
import kg.geoinfo.system.geoabstraction.repository.GeoAbstractJobRepository;
import kg.geoinfo.system.geoabstraction.repository.ImageryLayerRepository;
import kg.geoinfo.system.geoabstraction.repository.TerrainLayerRepository;
import kg.geoinfo.system.geoabstraction.service.filestore.FileStoreService;
import kg.geoinfo.system.geoabstraction.service.geoserver.GeoServerClient;
import kg.geoinfo.system.geoabstraction.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TerrainLayerRepository layerRepository;
    private final FileStoreService fileStoreService;
    private final KafkaProducerService kafkaProducerService;
    private final GeoAbstractMapper geoAbstractMapper;
    private final TerrainLayerMapper terrainLayerMapper;
    private final MinioProperties minioProperties;
    private final GeoServerClient geoServerClient;
    private final GeoServerProperties geoServerProperties;
    private final ImageryLayerService imageryLayerService;

    @Override
    @Transactional
    public GeoAbstractJobDto createJob(String name, MultipartFile file) {
        log.info("Creating terrain job with name {}", name);

        // 1. Save file to MinIO
        String objectKey = fileStoreService.save(file);

        // 2. Create job in DB
        GeoAbstractJob job = new GeoAbstractJob();
        job.setName(name);
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
    public GeoAbstractJobDto createSentinelJob(String name, MultipartFile file, List<String> channels, String indexType) {
        return this.createSatelliteJob(name, file, channels, indexType, "SENTINEL_COG");
    }

    @Override
    public GeoAbstractJobDto createLandsatJob(String name, MultipartFile file, List<String> channels, String indexType) {
        return this.createSatelliteJob(name, file, channels, indexType, "LANDSAT_COG");
    }

    @Override
    @Transactional
    public GeoAbstractJobDto createRawGeoTiffJob(String name, MultipartFile file) {
        return createSatelliteJob(name, file, null, null, "RAW_GEOTIFF_OPTIMIZE");
    }

    @Override
    @Transactional
    public GeoAbstractJobDto createTerrainJob(String name, MultipartFile file) {
        return createJob(name, file);
    }

    private GeoAbstractJobDto createSatelliteJob(String name, MultipartFile file, List<String> channels, String indexType, String taskType) {
        log.info("Creating {} job with name {}, channels {} and indexType {}", taskType, name, channels, indexType);

        // 1. Save file to MinIO
        String objectKey = fileStoreService.save(file);

        // 2. Create job in DB
        GeoAbstractJob job = new GeoAbstractJob();
        job.setName(name);
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
    public Page<GeoAbstractJobDto> getJobs(Pageable pageable) {
        return jobRepository.findAll(pageable)
                .map(geoAbstractMapper::toDto);
    }

    @Override
    public Page<TerrainLayerDto> getLayers(Pageable pageable) {
        return layerRepository.findAll(pageable)
                .map(terrainLayerMapper::toDto);
    }

    @Override
    @Transactional
    public void updateJobStatus(UUID jobId, String status, String errorMessage, Double minHeight, Double maxHeight, String terrainUrl) {
        log.info("Updating job {} status to {}", jobId, status);
        GeoAbstractJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        // If job is already in a final state, don't update it (prevents re-processing noise)
        if (job.getStatus() == GeoAbstractJobStatus.READY) {
            log.info("Job {} is already READY, ignoring update to {}", jobId, status);
            return;
        }

        // Normalize terrainUrl: Cesium needs the directory, not the layer.json file path
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
        jobRepository.save(job);

        if (jobStatus == GeoAbstractJobStatus.READY && "TERRAIN_MESH".equals(job.getTaskType())) {
            // Check if layer already exists
            if (!layerRepository.existsByJobId(jobId)) {
                // Create TerrainLayer
                TerrainLayer layer = new TerrainLayer();
                layer.setJob(job);
                layer.setTitle(job.getName());
                layer.setTerrainUrl(terrainUrl);
                layer.setStatus("READY");
                layerRepository.save(layer);
            }
        }

        if (jobStatus == GeoAbstractJobStatus.READY && 
           ("SENTINEL_COG".equals(job.getTaskType()) || "LANDSAT_COG".equals(job.getTaskType()) || "RAW_GEOTIFF_OPTIMIZE".equals(job.getTaskType()))) {
            
            String workspace = geoServerProperties.getWorkspace();
            String storeName = job.getOutputPrefix();
            String layerName = job.getOutputPrefix();
            String filePath = "/data/gdal-store/" + job.getOutputPrefix() + ".tif";
            
            // Determine style
            String styleName = "raster"; // default
            if (job.getCharacteristics() != null) {
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

            // 1. Create CoverageStore in GeoServer
            geoServerClient.createCoverageStore(workspace, storeName, filePath);
            
            // 2. Publish Layer
            geoServerClient.publishLayer(workspace, storeName, layerName, styleName);
            
            // 3. Create ImageryLayer in DB via Service (to trigger Kafka event)
            kg.geoinfo.system.geoabstraction.models.ImageryLayer imageryLayer = new kg.geoinfo.system.geoabstraction.models.ImageryLayer();
            imageryLayer.setName(job.getName());
            imageryLayer.setDescription("Automatically published layer from job " + job.getId());
            imageryLayer.setWorkspace(workspace);
            imageryLayer.setLayerName(layerName);
            imageryLayer.setServiceUrl(geoServerProperties.getUrl() + "/" + workspace + "/wms");
            imageryLayer.setStatus(kg.geoinfo.system.geoabstraction.models.enums.Status.ACTIVE);
            imageryLayer.setStyle(styleName);
            imageryLayer.setDateCaptured(new java.util.Date());
            imageryLayer.setCrs(job.getCrs() != null ? job.getCrs() : "EPSG:4326");
            imageryLayer.setCharacteristics(job.getCharacteristics());
            
            imageryLayerService.save(imageryLayer);
        }

        if (jobStatus == GeoAbstractJobStatus.READY) {
            // Cleanup source TIFF after successful import
            try {
                log.info("Cleaning up source file {} for job {}", job.getSourceObjectKey(), jobId);
                fileStoreService.delete(job.getSourceObjectKey());
            } catch (Exception e) {
                log.error("Failed to cleanup source file for job {}: {}", jobId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void deleteLayer(UUID id) {
        log.info("Deleting terrain layer {}", id);
        TerrainLayer layer = layerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Layer not found: " + id));

        GeoAbstractJob job = layer.getJob();
        if (job != null) {
            // Send event to Kafka to delete generated terrain files from local store
            log.info("Sending DELETED event for job {} with prefix {}", job.getId(), job.getOutputPrefix());
            GeoAbstractJobEvent event = GeoAbstractJobEvent.builder()
                    .jobId(job.getId())
                    .eventType(GeoAbstractJobEvent.EventType.DELETED)
                    .taskType(job.getTaskType())
                    .outputPrefix(job.getOutputPrefix())
                    .build();

            kafkaProducerService.sendGeoAbstractJobEvent(event);
            
            // Delete the layer
            layerRepository.delete(layer);
            
            // Delete the job record
            jobRepository.delete(job);
        } else {
            layerRepository.delete(layer);
        }
    }

}
