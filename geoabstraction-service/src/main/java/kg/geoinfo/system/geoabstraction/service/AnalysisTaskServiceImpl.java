package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.common.GeoAnalysisResultEvent;
import kg.geoinfo.system.common.GeoAnalysisTaskEvent;
import kg.geoinfo.system.common.GeoVectorExportRequest;
import kg.geoinfo.system.common.GeoVectorExportResponse;
import kg.geoinfo.system.geoabstraction.config.MinioProperties;
import kg.geoinfo.system.geoabstraction.dto.AnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.dto.CreateAnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.models.AnalysisTask;
import kg.geoinfo.system.geoabstraction.models.ImageryLayer;
import kg.geoinfo.system.geoabstraction.models.TerrainLayer;
import kg.geoinfo.system.geoabstraction.models.enums.AnalysisTaskStatus;
import kg.geoinfo.system.geoabstraction.models.RasterStyle;
import kg.geoinfo.system.geoabstraction.repository.AnalysisTaskRepository;
import kg.geoinfo.system.geoabstraction.repository.ImageryLayerRepository;
import kg.geoinfo.system.geoabstraction.repository.RasterStyleRepository;
import kg.geoinfo.system.geoabstraction.repository.TerrainLayerRepository;
import kg.geoinfo.system.geoabstraction.dto.CommitAnalysisTaskRequestDto;
import kg.geoinfo.system.geoabstraction.service.client.GeoDataServiceClient;
import kg.geoinfo.system.geoabstraction.service.filestore.FileStoreService;
import kg.geoinfo.system.geoabstraction.service.kafka.KafkaProducerService;
import kg.geoinfo.system.geoabstraction.models.PluginSchema;
import kg.geoinfo.system.geoabstraction.repository.PluginSchemaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskServiceImpl implements AnalysisTaskService {

    private final AnalysisTaskRepository repository;
    private final ImageryLayerRepository imageryLayerRepository;
    private final TerrainLayerRepository terrainLayerRepository;
    private final FileStoreService fileStoreService;
    private final KafkaProducerService kafkaProducerService;
    private final MinioProperties minioProperties;
    private final GeoDataServiceClient geoDataServiceClient;
    private final ImageryLayerService imageryLayerService;
    private final RasterStyleRepository rasterStyleRepository;
    private final PluginSchemaRepository pluginSchemaRepository;
    private final ObjectMapper objectMapper;


    @Override
    @Transactional
    public AnalysisTaskDto createTask(CreateAnalysisTaskDto dto) {
        validateTaskParameters(dto.getPluginName(), dto.getInputs(), dto.getParameters());
        
        UUID taskId = UUID.randomUUID();
        
        AnalysisTask task = new AnalysisTask();
        task.setId(taskId);
        task.setPluginName(dto.getPluginName());
        task.setStatus(AnalysisTaskStatus.PENDING);
        task.setInputParams(dto.getParameters());
        task.setProjectId(dto.getProjectId());
        
        Map<String, String> s3Inputs = new HashMap<>();
        boolean requiresVectorExport = false;

        for (Map.Entry<String, CreateAnalysisTaskDto.AnalysisDataSource> entry : dto.getInputs().entrySet()) {
            String key = entry.getKey();
            CreateAnalysisTaskDto.AnalysisDataSource source = entry.getValue();
            
            switch (source.getType()) {
                case IMAGERY_LAYER:
                    ImageryLayer layer = imageryLayerRepository.findById(source.getId())
                            .orElseThrow(() -> new RuntimeException("Imagery layer not found: " + source.getId()));
                    s3Inputs.put(key, "s3://" + minioProperties.getBucket() + "/" + layer.getCogObjectKey());
                    break;

                case TERRAIN_LAYER:
                    TerrainLayer terrainLayer = terrainLayerRepository.findById(source.getId())
                            .orElseThrow(() -> new RuntimeException("Terrain layer not found: " + source.getId()));
                    if (terrainLayer.getCogObjectKey() == null) {
                        throw new RuntimeException("Terrain layer " + source.getId() + " does not have a COG file. Run COG generation first.");
                    }
                    s3Inputs.put(key, "s3://" + minioProperties.getBucket() + "/" + terrainLayer.getCogObjectKey());
                    break;
                    
                case VECTOR_LAYER:
                    String exportPath = "temp/analysis/" + taskId + "/" + key + ".geojson";
                    s3Inputs.put(key, "export-pending:" + exportPath);
                    
                    kafkaProducerService.sendVectorExportRequest(GeoVectorExportRequest.builder()
                            .taskId(taskId)
                            .exportKey(key)
                            .layerId(source.getId())
                            .projectId(task.getProjectId())
                            .s3Destination(exportPath)
                            .build());
                    requiresVectorExport = true;
                    break;
                    
                case PREVIOUS_TASK_RESULT:
                    AnalysisTask prevTask = repository.findById(source.getTaskId())
                            .orElseThrow(() -> new RuntimeException("Previous task not found: " + source.getTaskId()));
                    String prevOutput = prevTask.getS3OutputPaths().get(source.getOutputKey());
                    if (prevOutput == null) {
                        throw new RuntimeException("Output key " + source.getOutputKey() + " not found in task " + source.getTaskId());
                    }
                    s3Inputs.put(key, prevOutput);
                    break;
                    
                case DIRECT_S3:
                    s3Inputs.put(key, source.getS3Url());
                    break;
            }
        }
        
        task.setS3InputPaths(s3Inputs);
        repository.save(task);

        if (!requiresVectorExport) {
            triggerWorker(task);
        }

        return mapToDto(task);
    }

    @Override
    @Transactional
    public void handleExportResponse(GeoVectorExportResponse response) {
        repository.findById(response.getTaskId()).ifPresent(task -> {
            if (task.getStatus() != AnalysisTaskStatus.PENDING) {
                log.warn("Received export response for task {} in status {}", task.getId(), task.getStatus());
                return;
            }

            if (!response.isSuccess()) {
                task.setStatus(AnalysisTaskStatus.FAILED);
                task.setErrorMessage("Vector export failed: " + response.getError());
                repository.save(task);
                return;
            }

            Map<String, String> s3Inputs = new HashMap<>(task.getS3InputPaths());
            s3Inputs.put(response.getExportKey(), response.getS3Url());
            task.setS3InputPaths(s3Inputs);
            repository.save(task);

            boolean allReady = task.getS3InputPaths().values().stream()
                    .noneMatch(path -> path != null && path.startsWith("export-pending:"));
            
            if (allReady) {
                triggerWorker(task);
            }
        });
    }

    private void triggerWorker(AnalysisTask task) {
        task.setStatus(AnalysisTaskStatus.PROCESSING);
        repository.save(task);
        
        kafkaProducerService.sendGeoAnalysisTaskEvent(GeoAnalysisTaskEvent.builder()
                .taskId(task.getId())
                .pluginName(task.getPluginName())
                .inputs(task.getS3InputPaths())
                .parameters(task.getInputParams())
                .build());
    }

    @Override
    @Transactional
    public void handleAnalysisResult(GeoAnalysisResultEvent event) {
        repository.findById(event.getTaskId()).ifPresentOrElse(task -> {
            task.setStatus(AnalysisTaskStatus.valueOf(event.getStatus()));
            task.setS3OutputPaths(event.getOutputs());
            task.setErrorMessage(event.getError());
            
            repository.save(task);
            log.info("Updated analysis task {} status to {}", task.getId(), task.getStatus());
        }, () -> log.error("Analysis task not found: {}", event.getTaskId()));
    }

    @Override
    public AnalysisTaskDto getTask(UUID taskId) {
        return repository.findById(taskId)
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
    }

    @Override
    public List<AnalysisTaskDto> getTasksByProjectId(UUID projectId) {
        return repository.findAllByProjectId(projectId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public String generateOutputPresignedUrl(UUID taskId, String outputKey) {
        AnalysisTask task = repository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        
        if (task.getS3OutputPaths() == null || !task.getS3OutputPaths().containsKey(outputKey)) {
            throw new RuntimeException("Output key " + outputKey + " not found for task " + taskId);
        }
        
        String s3Url = task.getS3OutputPaths().get(outputKey);
        if (s3Url == null || !s3Url.startsWith("s3://")) {
            throw new RuntimeException("Invalid S3 URL: " + s3Url);
        }
        
        String path = s3Url.substring(5); // Remove "s3://"
        int slashIndex = path.indexOf("/");
        if (slashIndex == -1) {
            throw new RuntimeException("Invalid S3 path structure: " + s3Url);
        }
        
        String bucket = path.substring(0, slashIndex);
        String key = path.substring(slashIndex + 1);
        
        String url = fileStoreService.generateDownloadUrl(bucket, key);
        
        // Rewrite internal MinIO URL to public /minio/ prefix
        if (url.contains("/" + bucket + "/")) {
            url = "/minio" + url.substring(url.indexOf("/" + bucket + "/"));
        }
        
        return url;
    }

    @Override
    @Transactional
    public void commitTask(UUID taskId, CommitAnalysisTaskRequestDto dto) {
        AnalysisTask task = repository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
        
        if (task.getStatus() != AnalysisTaskStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED tasks can be committed. Current status: " + task.getStatus());
        }

        // 1. Commit vector parts via geodata-service
        geoDataServiceClient.commitTask(taskId, dto);

        // 2. Commit raster parts (if any)
        if (task.getS3OutputPaths() != null && task.getS3OutputPaths().containsKey("raster_result")) {
            String s3Url = task.getS3OutputPaths().get("raster_result");
            if (s3Url != null && s3Url.startsWith("s3://")) {
                try {
                    String path = s3Url.substring(5); // Remove "s3://"
                    int slashIndex = path.indexOf("/");
                    if (slashIndex != -1) {
                        String sourceBucket = path.substring(0, slashIndex);
                        String sourceKey = path.substring(slashIndex + 1);
                        String destinationKey = "imagery-cog/" + UUID.randomUUID() + ".tif";

                        // Copy file to permanent storage and delete the staging one
                        fileStoreService.copy(sourceBucket, sourceKey, minioProperties.getBucket(), destinationKey);
                        fileStoreService.delete(sourceBucket, sourceKey);

                        // Create metadata for ImageryLayer
                        ImageryLayer imageryLayer = new ImageryLayer();
                        imageryLayer.setProjectId(task.getProjectId());
                        
                        String layerTitle = dto.getTaskName() != null ? dto.getTaskName() : task.getPluginName() + " Result";
                        imageryLayer.setName(layerTitle);
                        imageryLayer.setDescription("Committed raster result from analysis task " + taskId);
                        
                        String cleanPluginName = task.getPluginName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
                        String generatedLayerName = "analysis_" + cleanPluginName + "_" + UUID.randomUUID().toString().substring(0, 8);
                        imageryLayer.setLayerName(generatedLayerName);
                        
                        imageryLayer.setStatus(kg.geoinfo.system.geoabstraction.models.enums.Status.ACTIVE);
                        imageryLayer.setDateCaptured(new java.util.Date());
                        imageryLayer.setCrs("EPSG:3857");
                        imageryLayer.setCogObjectKey(destinationKey);

                        // Match style based on indexType (if present)
                        String styleName = "raster";
                        if (task.getInputParams() != null && task.getInputParams().containsKey("indexType")) {
                            String indexType = (String) task.getInputParams().get("indexType");
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
                        
                        RasterStyle rasterStyle = rasterStyleRepository.findByName(styleName)
                                .orElseGet(() -> rasterStyleRepository.findByName("raster")
                                        .orElseThrow(() -> new RuntimeException("Default style 'raster' not found")));
                        imageryLayer.setStyle(rasterStyle);

                        // Save imagery layer and trigger Kafka event via service
                        imageryLayerService.save(imageryLayer);
                        log.info("Successfully committed raster result for task {} to project {}", taskId, task.getProjectId());
                    }
                } catch (Exception e) {
                    log.error("Failed to commit raster result for task {}: {}", taskId, e.getMessage(), e);
                    throw new RuntimeException("Failed to commit raster result: " + e.getMessage(), e);
                }
            }
        }

        task.setStatus(AnalysisTaskStatus.COMMITTED);
        repository.save(task);
    }

    @Override
    @Transactional
    public void rollbackTask(UUID taskId) {
        AnalysisTask task = repository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        // Delete files from S3 if s3OutputPaths is not null
        if (task.getS3OutputPaths() != null) {
            for (String s3Url : task.getS3OutputPaths().values()) {
                if (s3Url != null && s3Url.startsWith("s3://")) {
                    try {
                        String path = s3Url.substring(5); // Remove "s3://"
                        int slashIndex = path.indexOf("/");
                        if (slashIndex != -1) {
                            String bucket = path.substring(0, slashIndex);
                            String key = path.substring(slashIndex + 1);
                            fileStoreService.delete(bucket, key);
                            log.info("Deleted staging file from S3: bucket={}, key={}", bucket, key);
                        }
                    } catch (Exception e) {
                        log.error("Failed to delete staging file {} from S3: {}", s3Url, e.getMessage());
                    }
                }
            }
        }

        geoDataServiceClient.rollbackTask(taskId);

        task.setStatus(AnalysisTaskStatus.ARCHIVED);
        repository.save(task);
    }

    private AnalysisTaskDto mapToDto(AnalysisTask entity) {
        AnalysisTaskDto dto = new AnalysisTaskDto();
        dto.setId(entity.getId());
        dto.setPluginName(entity.getPluginName());
        dto.setStatus(entity.getStatus());
        dto.setInputParams(entity.getInputParams());
        dto.setS3InputPaths(entity.getS3InputPaths());
        dto.setS3OutputPaths(entity.getS3OutputPaths());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setUserId(entity.getUserId());
        dto.setProjectId(entity.getProjectId());
        return dto;
    }

    @Override
    public List<PluginSchema> getRegisteredSchemas() {
        return pluginSchemaRepository.findAll();
    }

    private void validateTaskParameters(String pluginName, Map<String, CreateAnalysisTaskDto.AnalysisDataSource> inputs, Map<String, Object> parameters) {
        pluginSchemaRepository.findById(pluginName).ifPresent(pluginSchema -> {
            try {
                Map<String, Object> payloadToValidate = new HashMap<>();
                
                Map<String, String> inputsPlaceholder = new HashMap<>();
                if (inputs != null) {
                    for (String key : inputs.keySet()) {
                        inputsPlaceholder.put(key, "layer_id_placeholder");
                    }
                }
                
                payloadToValidate.put("inputs", inputsPlaceholder);
                payloadToValidate.put("parameters", parameters != null ? parameters : Map.of());

                com.fasterxml.jackson.databind.JsonNode payloadNode = objectMapper.valueToTree(payloadToValidate);
                com.fasterxml.jackson.databind.JsonNode schemaNode = objectMapper.valueToTree(pluginSchema.getSchema());

                com.networknt.schema.JsonSchemaFactory factory = com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V7);
                com.networknt.schema.JsonSchema jsonSchema = factory.getSchema(schemaNode);

                java.util.Set<com.networknt.schema.ValidationMessage> errors = jsonSchema.validate(payloadNode);
                if (!errors.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Ошибка валидации параметров задачи: ");
                    for (com.networknt.schema.ValidationMessage error : errors) {
                        sb.append(error.getMessage()).append("; ");
                    }
                    throw new IllegalArgumentException(sb.toString());
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to perform JSON schema validation for plugin {}: {}", pluginName, e.getMessage(), e);
            }
        });
    }
}
