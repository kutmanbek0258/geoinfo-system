package kg.geoinfo.system.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerrainJobEvent {
    private UUID jobId;
    private UUID projectId;
    private String name;
    private EventType eventType;
    private String sourceBucket;
    private String sourceObjectKey;
    private String outputBucket;
    private String outputPrefix;
    private String errorMessage;

    public enum EventType {
        CREATED,
        QUEUED,
        PROCESSING,
        READY,
        FAILED
    }
}
