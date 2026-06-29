package kg.geoinfo.system.geoabstraction.service.filestore;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import kg.geoinfo.system.geoabstraction.config.MinioProperties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStoreServiceImpl implements FileStoreService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    @SneakyThrows
    public void init() {
        String bucket = minioProperties.getBucket();
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        // Set public read policy for the bucket to allow CesiumJS to access tiles
        String policy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Action\": [\"s3:GetBucketLocation\", \"s3:ListBucket\"],\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Resource\": \"arn:aws:s3:::" + bucket + "\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"Action\": \"s3:GetObject\",\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Resource\": \"arn:aws:s3:::" + bucket + "/*\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(policy)
                        .build()
        );
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
    public String generateUploadUrl(String objectKey) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(minioProperties.getBucket())
                        .object(objectKey)
                        .expiry(60 * 60) // 1 hour
                        .build()
        );
    }

    @Override
    @SneakyThrows
    public String generateDownloadUrl(String objectKey) {
        return generateDownloadUrl(minioProperties.getBucket(), objectKey);
    }

    @Override
    @SneakyThrows
    public String generateDownloadUrl(String bucket, String objectKey) {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(24 * 60 * 60) // 24 hours
                        .build()
        );
    }

    @Override
    @SneakyThrows
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
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
            return Optional.empty();
        }
    }

    @Override
    @SneakyThrows
    public void delete(String key) {
        delete(minioProperties.getBucket(), key);
    }

    @Override
    @SneakyThrows
    public void delete(String bucket, String key) {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build());
    }

    @Override
    @SneakyThrows
    public void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        Iterable<Result<io.minio.messages.Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .prefix(prefix)
                        .recursive(true)
                        .build());

        List<DeleteObject> objectsToDelete = new ArrayList<>();
        for (Result<io.minio.messages.Item> result : results) {
            objectsToDelete.add(new DeleteObject(result.get().objectName()));
        }

        if (!objectsToDelete.isEmpty()) {
            Iterable<Result<DeleteError>> deleteResults = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .objects(objectsToDelete)
                            .build());

            // Iterate to trigger the deletion
            for (Result<DeleteError> result : deleteResults) {
                DeleteError error = result.get();
                log.error("Error deleting object {}: {}", error.objectName(), error.message());
            }
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

    @Override
    @SneakyThrows
    public void copy(String sourceKey, String destinationKey) {
        copy(minioProperties.getBucket(), sourceKey, minioProperties.getBucket(), destinationKey);
    }

    @Override
    @SneakyThrows
    public void copy(String sourceBucket, String sourceKey, String destinationBucket, String destinationKey) {
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(destinationBucket)
                        .object(destinationKey)
                        .source(
                                CopySource.builder()
                                        .bucket(sourceBucket)
                                        .object(sourceKey)
                                        .build()
                        )
                        .build()
        );
        log.info("Copied object in MinIO from bucket {} key {} to bucket {} key {}", sourceBucket, sourceKey, destinationBucket, destinationKey);
    }
}
