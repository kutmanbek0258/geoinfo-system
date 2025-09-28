package kg.geoinfo.system.docservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private UUID id;
    private UUID geoObjectId;
    private String fileName;
    private String mimeType;
    private Long fileSizeBytes;
    private String description;
    private UUID uploadedByUserId;
    private ZonedDateTime uploadDate;
    private boolean isLatestVersion;
    private Set<TagDto> tags;
}
