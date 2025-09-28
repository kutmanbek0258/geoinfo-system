package kg.geoinfo.system.docservice.service.filestore;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface FileStoreService {

    /**
     * Saves a file to the file store.
     *
     * @param file the multipart file to save
     * @return the unique key assigned to the stored file
     */
    String save(MultipartFile file);

    /**
     * Retrieves a file from the file store.
     *
     * @param key the unique key of the file to retrieve
     * @return an Optional containing the file as a byte array, or empty if not found
     */
    Optional<byte[]> find(String key);

    /**
     * Deletes a file from the file store.
     *
     * @param key the unique key of the file to delete
     */
    void delete(String key);
}
