package kg.geoinfo.system.authservice.mapper;

import kg.geoinfo.system.authservice.dao.entity.FileStoreEntity;
import kg.geoinfo.system.authservice.dto.FileStoreDto;

public class FileStoreMapper {

    public static FileStoreDto map(FileStoreEntity entity) {
        return FileStoreDto.builder()
            .bucket(entity.getBucket())
            .contentType(entity.getContentType())
            .id(entity.getId())
            .name(entity.getFilename())
            .size(entity.getFileSize())
            .build();
    }
}
