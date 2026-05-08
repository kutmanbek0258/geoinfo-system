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
    public TerrainJobDto createJob(UUID projectId, String name, MultipartFile file) {
        log.info("Creating terrain job for project {} with name {}", projectId, name);

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
        job.setOutputPrefix("terrain/" + job.getName() + "-" + UUID.randomUUID().toString().substring(0, 8));

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

        TerrainJobStatus jobStatus = TerrainJobStatus.valueOf(status);
        job.setStatus(jobStatus);
        job.setErrorMessage(errorMessage);
        job.setMinHeight(minHeight);
        job.setMaxHeight(maxHeight);
        jobRepository.save(job);

        if (jobStatus == TerrainJobStatus.READY) {
            // Create or update TerrainLayer
            TerrainLayer layer = new TerrainLayer();
            layer.setJob(job);
            layer.setTitle(job.getName());
            layer.setTerrainUrl(terrainUrl);
            layer.setStatus("READY");
            layerRepository.save(layer);
        }
    }
}
