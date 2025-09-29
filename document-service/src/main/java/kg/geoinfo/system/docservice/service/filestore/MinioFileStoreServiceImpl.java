package kg.geoinfo.system.docservice.service.filestore;

import io.minio.*;
import io.minio.http.Method;
import kg.geoinfo.system.docservice.config.MinioProperties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioFileStoreServiceImpl implements FileStoreService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    @SneakyThrows
    public void init() {
        // Create the bucket if it doesn't exist
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
        }
    }

    @Override
    @SneakyThrows
    public String save(MultipartFile file) {
        String objectKey = UUID.randomUUID().toString();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());

        return objectKey;
    }

    @Override
    @SneakyThrows
    public Optional<byte[]> find(String key) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(key)
                        .build())) {
            return Optional.of(stream.readAllBytes());
        } catch (Exception e) {
            // Log the exception properly in a real application
            return Optional.empty();
        }
    }

    @Override
    @SneakyThrows
    public void delete(String key) {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(key)
                        .build());
    }

    @Override
    public URL generatePresignedUrl(String fileKey, long expirySeconds) {
        try {
            String urlStr = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(fileKey)
                            .method(Method.GET)
                            .expiry((int) expirySeconds)
                            .build()
            );
            return new URL(urlStr);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned url", e);
        }
    }

    @Override
    public void overwrite(String fileKey, InputStream in) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(fileKey)
                            .stream(in, -1, 10485760) // 10MB parts
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to overwrite file in MinIO", e);
        }
    }
}
