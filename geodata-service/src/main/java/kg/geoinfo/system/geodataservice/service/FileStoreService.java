package kg.geoinfo.system.geodataservice.service;

import java.io.InputStream;

public interface FileStoreService {
    void uploadFile(String bucket, String key, InputStream inputStream, String contentType);
    void deleteFile(String bucket, String key);
    String generateDownloadUrl(String objectKey);
    String generateDownloadUrl(String bucket, String objectKey);
}
