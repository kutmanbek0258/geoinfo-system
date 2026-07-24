package kg.geoinfo.system.geodataservice.service;

import kg.geoinfo.system.geodataservice.models.*;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис переноса (Commit) временных геоаналитических результатов в постоянные слои проекта,
 * а также удаления временных данных без переноса (Rollback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisCommitService {

    private final TempAnalysisGeometryRepository tempRepository;
    private final ProjectRepository projectRepository;
    private final GeoFolderRepository folderRepository;
    private final ProjectPointRepository pointRepository;
    private final ProjectMultilineRepository multilineRepository;
    private final ProjectPolygonRepository polygonRepository;
    private final LayerRepository layerRepository;

    /**
     * Переносит все временные геометрии задачи в постоянные таблицы проекта.
     *
     * @param taskId    идентификатор аналитической задачи
     * @param projectId идентификатор проекта
     * @param folderId  опциональный идентификатор папки (может быть null)
     * @param taskName  имя задачи — используется как префикс имён объектов
     */
    @Transactional
    public void commitTask(UUID taskId, UUID projectId, String targetName) {
        List<TempAnalysisGeometry> temps = tempRepository.findByTaskId(taskId);
        if (temps.isEmpty()) {
            log.warn("No staged geometries found for task {}", taskId);
            return;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        // Create new Layer automatically with targetName
        Layer layer = Layer.builder()
                .project(project)
                .name(targetName)
                .type(kg.geoinfo.system.geodataservice.models.enums.LayerType.VECTOR)
                .build();
        layer = layerRepository.save(layer);

        // Create new Folder automatically under the new layer with targetName
        GeoFolder folder = GeoFolder.builder()
                .layer(layer)
                .name(targetName)
                .build();
        folder = folderRepository.save(folder);

        int index = 1;
        for (TempAnalysisGeometry temp : temps) {
            Geometry geom = temp.getGeom();
            if (geom == null) {
                // Растровая запись-маркер — пропускаем, не переносим в векторные слои
                log.debug("Skipping raster marker for task {}", taskId);
                continue;
            }

            String objectName = resolveObjectName(temp.getProperties(), targetName, index++);

            String geomType = geom.getGeometryType();
            switch (geomType) {
                case "Point":
                case "MultiPoint":
                    commitPoint(project, folder, layer, objectName, temp);
                    break;
                case "LineString":
                case "MultiLineString":
                    commitLine(project, folder, layer, objectName, temp);
                    break;
                case "Polygon":
                case "MultiPolygon":
                    commitPolygon(project, folder, layer, objectName, temp);
                    break;
                default:
                    log.warn("Unknown geometry type '{}' for task {} — skipping", geomType, taskId);
            }
        }

        // Удаляем временные данные после успешного переноса
        tempRepository.deleteByTaskId(taskId);
        log.info("Committed {} geometry records for task {} into project {}", temps.size(), taskId, projectId);
    }

    /**
     * Удаляет все временные данные задачи без переноса в постоянные слои.
     *
     * @param taskId идентификатор аналитической задачи
     */
    @Transactional
    public void rollbackTask(UUID taskId) {
        tempRepository.deleteByTaskId(taskId);
        log.info("Rolled back (deleted) staged geometries for task {}", taskId);
    }

    // ─── Вспомогательные методы ─────────────────────────────────────────────

    /**
     * Определяет имя геообъекта.
     * Приоритет: поле "name" из properties → "taskName #index".
     */
    private String resolveObjectName(Map<String, Object> properties, String taskName, int index) {
        if (properties != null && properties.containsKey("name")) {
            Object nameVal = properties.get("name");
            if (nameVal != null && !nameVal.toString().isBlank()) {
                return nameVal.toString();
            }
        }
        return taskName + " #" + index;
    }

    private void commitPoint(Project project, GeoFolder folder, Layer layer, String name, TempAnalysisGeometry temp) {
        ProjectPoint point = new ProjectPoint();
        point.setProject(project);
        point.setFolder(folder);
        point.setLayer(layer);
        point.setName(name);
        point.setStatus(Status.COMPLETED);
        point.setCharacteristics(temp.getProperties());
        point.setGeom(GeometryUtils.ensureMultiPoint3D(temp.getGeom()));
        pointRepository.save(point);
    }

    private void commitLine(Project project, GeoFolder folder, Layer layer, String name, TempAnalysisGeometry temp) {
        ProjectMultiline line = new ProjectMultiline();
        line.setProject(project);
        line.setFolder(folder);
        line.setLayer(layer);
        line.setName(name);
        line.setStatus(Status.COMPLETED);
        line.setCharacteristics(temp.getProperties());
        line.setGeom(GeometryUtils.ensureMultiLineString3D(temp.getGeom()));
        multilineRepository.save(line);
    }

    private void commitPolygon(Project project, GeoFolder folder, Layer layer, String name, TempAnalysisGeometry temp) {
        ProjectPolygon polygon = new ProjectPolygon();
        polygon.setProject(project);
        polygon.setFolder(folder);
        polygon.setLayer(layer);
        polygon.setName(name);
        polygon.setStatus(Status.COMPLETED);
        polygon.setCharacteristics(temp.getProperties());
        polygon.setGeom(GeometryUtils.ensureMultiPolygon3D(temp.getGeom()));
        polygonRepository.save(polygon);
    }
}
