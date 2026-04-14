package com.digitalid.api.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local-disk storage — active when GCS_BUCKET_NAME is not set.
 * Files are stored at: {app.uploads.dir}/documents/{userId}/{userId}_{documentType}_{seq}{ext}
 */
@Service
@ConditionalOnExpression("'${app.gcs.bucket:}'.isEmpty()")
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    @PostConstruct
    public void init() {
        log.info("Storage backend: LOCAL DISK — uploads dir: {}", uploadsDir);
    }

    @Override
    public String store(Long userId, String documentType, int sequenceNumber,
                        String originalFilename, MultipartFile file) throws IOException {
        String ext        = extension(originalFilename);
        String storedName = userId + "_" + documentType + "_" + sequenceNumber + ext;
        Path   userDir    = Paths.get(uploadsDir, "documents", String.valueOf(userId));
        Files.createDirectories(userDir);
        Path dest = userDir.resolve(storedName).toAbsolutePath();
        log.debug("[LocalStorage] Saving {} ({} bytes) → {}", originalFilename, file.getSize(), dest);
        file.transferTo(dest.toFile());
        log.debug("[LocalStorage] Saved → {}", dest);
        return dest.toString();
    }

    @Override
    public Resource load(String storedPath) throws IOException {
        log.debug("[LocalStorage] Loading {}", storedPath);
        Path path = Paths.get(storedPath);
        UrlResource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            log.warn("[LocalStorage] File not found on disk: {}", storedPath);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
        }
        return resource;
    }

    @Override
    public String storeWithPath(String relPath, String contentType, MultipartFile file) throws IOException {
        Path dest = Paths.get(uploadsDir, relPath).toAbsolutePath();
        Files.createDirectories(dest.getParent());
        log.debug("[LocalStorage] Saving (raw path) {} ({} bytes) → {}", file.getOriginalFilename(), file.getSize(), dest);
        file.transferTo(dest.toFile());
        log.debug("[LocalStorage] Saved → {}", dest);
        return dest.toString();
    }

    @Override
    public void delete(String storedPath) {
        log.debug("[LocalStorage] Deleting {}", storedPath);
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(storedPath));
            if (deleted) log.debug("[LocalStorage] Deleted {}", storedPath);
            else log.debug("[LocalStorage] File not found for deletion: {}", storedPath);
        } catch (IOException e) {
            log.warn("[LocalStorage] Delete failed for {}: {}", storedPath, e.getMessage());
        }
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
