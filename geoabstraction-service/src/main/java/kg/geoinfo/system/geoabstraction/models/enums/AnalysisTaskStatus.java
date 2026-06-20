package kg.geoinfo.system.geoabstraction.models.enums;

public enum AnalysisTaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    /** Результат перенесён в постоянные слои проекта */
    COMMITTED,
    /** Временные данные удалены (Rollback) */
    ARCHIVED
}
