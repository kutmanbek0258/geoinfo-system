package kg.geoinfo.system.geodataservice.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Запрос на перенос результатов аналитической задачи в постоянные слои проекта.
 */
@Data
public class CommitAnalysisTaskDto {
    /** Идентификатор проекта, в который переносятся данные */
    private UUID projectId;
    /** Опциональная папка для новых объектов */
    private UUID folderId;
    /** Опциональный слой для новых объектов */
    private UUID layerId;
    /** Человекочитаемое имя задачи — используется как префикс имён объектов */
    private String taskName;
}
