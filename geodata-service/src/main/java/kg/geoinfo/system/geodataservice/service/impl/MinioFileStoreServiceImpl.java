package kg.geoinfo.system.geodataservice.service.impl;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import kg.geoinfo.system.geodataservice.config.MinioProperties;
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
    private final MinioProperties minioProperties;

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

    @Override
    public void deleteFile(String bucket, String key) {
        try {
            minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );
            log.info("Deleted file from MinIO: bucket={}, key={}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: bucket={}, key={}, error={}", bucket, key, e.getMessage());
            throw new RuntimeException("MinIO delete failed", e);
        }
    }

    @Override
    public String generateDownloadUrl(String objectKey) {
        return generateDownloadUrl(minioProperties.getBucket(), objectKey);
    }

    @Override
    public String generateDownloadUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(24 * 60 * 60) // 24 hours
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned download URL from MinIO: bucket={}, key={}, error={}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("MinIO URL generation failed", e);
        }
    }

    @Override
    public InputStream getFileStream(String bucket, String key) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get file stream from MinIO: bucket={}, key={}, error={}", bucket, key, e.getMessage());
            throw new RuntimeException("MinIO stream fetch failed", e);
        }
    }
}

