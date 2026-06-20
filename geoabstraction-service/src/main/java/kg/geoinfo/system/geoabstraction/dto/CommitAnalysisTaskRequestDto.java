package kg.geoinfo.system.geoabstraction.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CommitAnalysisTaskRequestDto {
    /** Идентификатор проекта, в который переносятся данные */
    private UUID projectId;
    /** Опциональная папка для новых объектов */
    private UUID folderId;
    /** Человекочитаемое имя задачи — используется как префикс имён объектов */
    private String taskName;
}
