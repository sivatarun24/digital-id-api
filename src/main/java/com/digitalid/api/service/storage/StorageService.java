package com.digitalid.api.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {

    /**
     * Stores a file and returns the storedPath used to retrieve or delete it later.
     * Dev:  absolute local path  (e.g. /app/uploads/documents/42/42_drivers_license_1.jpg)
     * Prod: GCS object name      (e.g. documents/42/42_drivers_license_1.jpg)
     *
     * @param sequenceNumber 1-based count of documents of this type already owned by the user + 1
     */
    String store(Long userId, String documentType, int sequenceNumber,
                 String originalFilename, MultipartFile file) throws IOException;

    /**
     * Stores a file at an arbitrary relative path.
     * Dev:  absolute local path  (e.g. /app/uploads/info-responses/7/0_doc.pdf)
     * Prod: GCS object name      (e.g. info-responses/7/0_doc.pdf)
     *
     * @param relPath     relative path such as "info-responses/{requestId}/{filename}"
     * @param contentType MIME type of the file
     */
    String storeWithPath(String relPath, String contentType, MultipartFile file) throws IOException;

    Resource load(String storedPath) throws IOException;

    void delete(String storedPath);
}
