package kg.geoinfo.system.geoprintservice.service;

import io.minio.*;
import io.minio.http.Method;
import kg.geoinfo.system.geoprintservice.config.MinioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public void uploadFile(String objectName, InputStream inputStream, String contentType) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(inputStream, inputStream.available(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error uploading file to MinIO", e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Error deleting file from MinIO: {}", objectName, e);
        }
    }

    public String getPresignedUrl(String objectName) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );

            // Transform internal URL to external one if externalEndpoint is provided
            if (minioProperties.getExternalEndpoint() != null && !minioProperties.getExternalEndpoint().isEmpty()) {
                // Example: replace http://minio:9000 with http://localhost/minio
                return url.replace(minioProperties.getEndpoint(), minioProperties.getExternalEndpoint());
            }

            return url;
        } catch (Exception e) {
            log.error("Error generating presigned URL", e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
