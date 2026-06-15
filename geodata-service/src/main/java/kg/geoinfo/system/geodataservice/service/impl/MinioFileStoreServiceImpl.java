package kg.geoinfo.system.geodataservice.service.impl;

import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import kg.geoinfo.system.geodataservice.service.FileStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStoreServiceImpl implements FileStoreService {

    private final MinioClient minioClient;

    @Override
    public void uploadFile(String bucket, String key, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(inputStream, -1, 10485760) // 10MB part size
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: bucket={}, key={}, error={}", bucket, key, e.getMessage());
            throw new RuntimeException("MinIO upload failed", e);
        }
    }
}
