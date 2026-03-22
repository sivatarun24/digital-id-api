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

    Resource load(String storedPath) throws IOException;

    void delete(String storedPath);
}
