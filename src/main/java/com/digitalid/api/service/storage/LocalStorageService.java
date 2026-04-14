package com.digitalid.api.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
 * Local-disk storage — active on every profile except "prod".
 * Files are stored at: {app.uploads.dir}/documents/{userId}/{userId}_{documentType}_{seq}{ext}
 */
@Service
@Profile("!prod")
public class LocalStorageService implements StorageService {

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    @Override
    public String store(Long userId, String documentType, int sequenceNumber,
                        String originalFilename, MultipartFile file) throws IOException {
        String ext        = extension(originalFilename);
        String storedName = userId + "_" + documentType + "_" + sequenceNumber + ext;
        Path   userDir    = Paths.get(uploadsDir, "documents", String.valueOf(userId));
        Files.createDirectories(userDir);
        Path dest = userDir.resolve(storedName).toAbsolutePath();
        file.transferTo(dest.toFile());
        return dest.toString();
    }

    @Override
    public Resource load(String storedPath) throws IOException {
        Path      path     = Paths.get(storedPath);
        UrlResource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
        }
        return resource;
    }

    @Override
    public String storeWithPath(String relPath, String contentType, MultipartFile file) throws IOException {
        Path dest = Paths.get(uploadsDir, relPath).toAbsolutePath();
        Files.createDirectories(dest.getParent());
        file.transferTo(dest.toFile());
        return dest.toString();
    }

    @Override
    public void delete(String storedPath) {
        try { Files.deleteIfExists(Paths.get(storedPath)); } catch (IOException ignored) {}
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
