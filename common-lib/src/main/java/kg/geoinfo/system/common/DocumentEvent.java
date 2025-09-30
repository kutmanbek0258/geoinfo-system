
package kg.geoinfo.system.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEvent {

    private EventType eventType;
    private Map<String, Object> payload;

    public enum EventType {
        CREATED,
        UPDATED,
        DELETED
    }
}
