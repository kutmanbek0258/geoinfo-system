
package kg.geoinfo.system.geodataservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoObjectEvent {

    private EventType eventType;
    private Map<String, Object> payload;

    public enum EventType {
        CREATED,
        UPDATED,
        DELETED
    }
}
