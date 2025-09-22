package kg.geoinfo.system.authservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class FileStoreDto {

    private UUID id;
    private String name;
    private String bucket;
    private String contentType;
    private Long size;
}
