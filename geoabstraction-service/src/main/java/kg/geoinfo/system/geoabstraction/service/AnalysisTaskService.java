package kg.geoinfo.system.geoabstraction.service;

import kg.geoinfo.system.geoabstraction.dto.CommitAnalysisTaskRequestDto;
import kg.geoinfo.system.common.GeoAnalysisResultEvent;
import kg.geoinfo.system.common.GeoVectorExportResponse;
import kg.geoinfo.system.geoabstraction.dto.AnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.dto.CreateAnalysisTaskDto;
import kg.geoinfo.system.geoabstraction.models.AnalysisTask;
import kg.geoinfo.system.geoabstraction.models.PluginSchema;

import java.util.List;
import java.util.UUID;

public interface AnalysisTaskService {
    AnalysisTaskDto createTask(CreateAnalysisTaskDto dto);
    void handleAnalysisResult(GeoAnalysisResultEvent event);
    void handleExportResponse(GeoVectorExportResponse response);
    AnalysisTaskDto getTask(UUID taskId);
    List<AnalysisTaskDto> getTasksByProjectId(UUID projectId);
    String generateOutputPresignedUrl(UUID taskId, String outputKey);
    void commitTask(UUID taskId, CommitAnalysisTaskRequestDto dto);
    void rollbackTask(UUID taskId);
    List<PluginSchema> getRegisteredSchemas();
}

