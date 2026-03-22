package com.digitalid.api.service.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * GCP Cloud Storage — active only in the "prod" profile.
 *
 * Object naming: documents/{userId}/{userId}_{documentType}_{seq}{ext}
 * e.g.           documents/42/42_drivers_license_1.jpg
 *
 * Authentication: Application Default Credentials (ADC).
 * On Cloud Run the service account attached to the revision is used automatically —
 * no key file needed. Grant that service account "Storage Object User" on the bucket.
 */
@Service
@Profile("prod")
public class GcsStorageService implements StorageService {

    @Value("${app.gcs.bucket}")
    private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @Override
    public String store(Long userId, String documentType, int sequenceNumber,
                        String originalFilename, MultipartFile file) throws IOException {
        String ext        = extension(originalFilename);
        String objectName = "documents/" + userId + "/"
                + userId + "_" + documentType + "_" + sequenceNumber + ext;

        BlobId   blobId   = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        storage.create(blobInfo, file.getBytes());
        return objectName; // stored as filePath in DB
    }

    @Override
    public Resource load(String storedPath) throws IOException {
        Blob blob = storage.get(BlobId.of(bucketName, storedPath));
        if (blob == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found in storage");
        }
        byte[] content = blob.getContent();
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return storedPath.substring(storedPath.lastIndexOf('/') + 1);
            }
        };
    }

    @Override
    public void delete(String storedPath) {
        try { storage.delete(BlobId.of(bucketName, storedPath)); } catch (Exception ignored) {}
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
