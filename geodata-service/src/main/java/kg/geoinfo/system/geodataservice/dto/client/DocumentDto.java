package kg.geoinfo.system.geodataservice.dto.client;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class DocumentDto {
    private UUID id;
    private String fileName;
    private String minioObjectKey;
    private String mimeType;
    private Long fileSizeBytes;
}
