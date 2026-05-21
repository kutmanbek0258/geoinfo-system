package kg.geoinfo.system.geoabstraction.service.filestore;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.Optional;

public interface FileStoreService {
    String save(MultipartFile file);
    String generateUploadUrl(String objectKey);
    boolean exists(String objectKey);
    Optional<byte[]> find(String key);
    void delete(String key);
    void deleteByPrefix(String prefix);
    void overwrite(String fileKey, InputStream in);
}
