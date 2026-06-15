package kg.geoinfo.system.geodataservice.service;

import java.io.InputStream;

public interface FileStoreService {
    void uploadFile(String bucket, String key, InputStream inputStream, String contentType);
}
