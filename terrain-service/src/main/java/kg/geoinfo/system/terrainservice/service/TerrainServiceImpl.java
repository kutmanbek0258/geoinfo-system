package kg.geoinfo.system.terrainservice.service;

import kg.geoinfo.system.common.TerrainJobEvent;
import kg.geoinfo.system.terrainservice.config.MinioProperties;
import kg.geoinfo.system.terrainservice.dto.TerrainJobDto;
import kg.geoinfo.system.terrainservice.dto.TerrainLayerDto;
import kg.geoinfo.system.terrainservice.mapper.TerrainLayerMapper;
import kg.geoinfo.system.terrainservice.mapper.TerrainMapper;
import kg.geoinfo.system.terrainservice.models.TerrainJob;
import kg.geoinfo.system.terrainservice.models.TerrainLayer;
import kg.geoinfo.system.terrainservice.models.enums.TerrainJobStatus;
import kg.geoinfo.system.terrainservice.repository.TerrainJobRepository;
import kg.geoinfo.system.terrainservice.repository.TerrainLayerRepository;
import kg.geoinfo.system.terrainservice.service.filestore.FileStoreService;
import kg.geoinfo.system.terrainservice.service.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerrainServiceImpl implements TerrainService {

    private final TerrainJobRepository jobRepository;
    private final TerrainLayerRepository layerRepository;
    private final FileStoreService fileStoreService;
    private final KafkaProducerService kafkaProducerService;
    private final TerrainMapper terrainMapper;
    private final TerrainLayerMapper terrainLayerMapper;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public TerrainJobDto createJob(String name, MultipartFile file) {
        log.info("Creating terrain job with name {}", name);

        // 1. Save file to MinIO
        String objectKey = fileStoreService.save(file);

        // 2. Create job in DB
        TerrainJob job = new TerrainJob();
        job.setName(name);
        job.setStatus(TerrainJobStatus.QUEUED);
        job.setSourceBucket(minioProperties.getBucket());
        job.setSourceObjectKey(objectKey);
        job.setFileSize(file.getSize());
        job.setOutputBucket(minioProperties.getBucket());
        job.setOutputPrefix(job.getName() + "-" + UUID.randomUUID().toString().substring(0, 8));

        job = jobRepository.save(job);

        // 3. Send event to Kafka
        TerrainJobEvent event = TerrainJobEvent.builder()
                .jobId(job.getId())
                .name(job.getName())
                .eventType(TerrainJobEvent.EventType.QUEUED)
                .sourceBucket(job.getSourceBucket())
                .sourceObjectKey(job.getSourceObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputPrefix(job.getOutputPrefix())
                .build();

        kafkaProducerService.sendTerrainJobEvent(event);

        return terrainMapper.toDto(job);
    }

    @Override
    public TerrainJobDto getJob(UUID jobId) {
        TerrainJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        return terrainMapper.toDto(job);
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
        TerrainJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        // If job is already in a final state, don't update it (prevents re-processing noise)
        if (job.getStatus() == TerrainJobStatus.READY) {
            log.info("Job {} is already READY, ignoring update to {}", jobId, status);
            return;
        }

        // Normalize terrainUrl: Cesium needs the directory, not the layer.json file path
        if (terrainUrl != null && terrainUrl.endsWith("/layer.json")) {
            terrainUrl = terrainUrl.substring(0, terrainUrl.length() - 10);
        } else if (terrainUrl != null && terrainUrl.endsWith("/layer.json/")) {
            terrainUrl = terrainUrl.substring(0, terrainUrl.length() - 11);
        }

        TerrainJobStatus jobStatus = TerrainJobStatus.valueOf(status);
        job.setStatus(jobStatus);
        job.setErrorMessage(errorMessage);
        job.setMinHeight(minHeight);
        job.setMaxHeight(maxHeight);
        jobRepository.save(job);

        if (jobStatus == TerrainJobStatus.READY) {
            // Check if layer already exists
            boolean layerExists = layerRepository.findAll().stream()
                    .anyMatch(l -> l.getJob() != null && l.getJob().getId().equals(jobId));
            
            if (!layerExists) {
                // Create TerrainLayer
                TerrainLayer layer = new TerrainLayer();
                layer.setJob(job);
                layer.setTitle(job.getName());
                layer.setTerrainUrl(terrainUrl);
                layer.setStatus("READY");
                layerRepository.save(layer);
            }

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

        TerrainJob job = layer.getJob();
        if (job != null) {
            // Send event to Kafka to delete generated terrain files from local store
            log.info("Sending DELETED event for job {} with prefix {}", job.getId(), job.getOutputPrefix());
            TerrainJobEvent event = TerrainJobEvent.builder()
                    .jobId(job.getId())
                    .eventType(TerrainJobEvent.EventType.DELETED)
                    .outputPrefix(job.getOutputPrefix())
                    .build();

            kafkaProducerService.sendTerrainJobEvent(event);
            
            // Delete the layer
            layerRepository.delete(layer);
            
            // Delete the job record
            jobRepository.delete(job);
        } else {
            layerRepository.delete(layer);
        }
    }
}
