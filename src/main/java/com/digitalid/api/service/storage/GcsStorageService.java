package com.digitalid.api.service.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import jakarta.annotation.PostConstruct;
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
 *
 * Locally: set GCS_BUCKET_NAME env var and run `gcloud auth application-default login`.
 */
@Service
@ConditionalOnExpression("!'${app.gcs.bucket:}'.isEmpty()")
public class GcsStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageService.class);

    @Value("${app.gcs.bucket}")
    private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @PostConstruct
    public void init() {
        log.info("Storage backend: GOOGLE CLOUD STORAGE — bucket: {}", bucketName);
        try {
            Bucket bucket = storage.get(bucketName);
            if (bucket == null) {
                throw new IllegalStateException(
                        "GCS bucket '" + bucketName + "' does not exist or is not accessible. " +
                        "Check the bucket name and that credentials have 'Storage Object User' on it.");
            }
            log.info("[GCS] Bucket '{}' is accessible — storage ready", bucketName);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "GCS initialisation failed for bucket '" + bucketName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public String store(Long userId, String documentType, int sequenceNumber,
                        String originalFilename, MultipartFile file) throws IOException {
        String ext        = extension(originalFilename);
        String objectName = "documents/" + userId + "/"
                + userId + "_" + documentType + "_" + sequenceNumber + ext;

        log.debug("[GCS] Uploading {} ({} bytes, {}) → gs://{}/{}",
                originalFilename, file.getSize(), file.getContentType(), bucketName, objectName);

        BlobId   blobId   = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        storage.create(blobInfo, file.getBytes());
        log.debug("[GCS] Upload complete → gs://{}/{}", bucketName, objectName);
        return objectName; // stored as filePath in DB
    }

    @Override
    public Resource load(String storedPath) throws IOException {
        // A path starting with '/' is an absolute local disk path written by LocalStorageService.
        // That file was never uploaded to GCS — the document must be re-uploaded.
        if (storedPath != null && storedPath.startsWith("/")) {
            log.warn("[GCS] storedPath '{}' is a local filesystem path — file was not uploaded to GCS", storedPath);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This document was stored on local disk before cloud storage was enabled. Please delete and re-upload it.");
        }
        log.debug("[GCS] Loading gs://{}/{}", bucketName, storedPath);
        Blob blob = storage.get(BlobId.of(bucketName, storedPath));
        if (blob == null) {
            log.warn("[GCS] Object not found: gs://{}/{}", bucketName, storedPath);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found in storage");
        }
        byte[] content = blob.getContent();
        log.debug("[GCS] Loaded {} bytes from gs://{}/{}", content.length, bucketName, storedPath);
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return storedPath.substring(storedPath.lastIndexOf('/') + 1);
            }
        };
    }

    @Override
    public String storeWithPath(String relPath, String contentType, MultipartFile file) throws IOException {
        log.debug("[GCS] Uploading (raw path) {} ({} bytes, {}) → gs://{}/{}",
                file.getOriginalFilename(), file.getSize(), contentType, bucketName, relPath);

        BlobId   blobId   = BlobId.of(bucketName, relPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType != null ? contentType : "application/octet-stream")
                .build();
        storage.create(blobInfo, file.getBytes());
        log.debug("[GCS] Upload complete → gs://{}/{}", bucketName, relPath);
        return relPath;
    }

    @Override
    public void delete(String storedPath) {
        if (storedPath != null && storedPath.startsWith("/")) {
            log.debug("[GCS] Skipping delete — '{}' is a local filesystem path, not a GCS object", storedPath);
            return;
        }
        log.debug("[GCS] Deleting gs://{}/{}", bucketName, storedPath);
        try {
            storage.delete(BlobId.of(bucketName, storedPath));
            log.debug("[GCS] Deleted gs://{}/{}", bucketName, storedPath);
        } catch (Exception e) {
            log.warn("[GCS] Delete failed for gs://{}/{}: {}", bucketName, storedPath, e.getMessage());
        }
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
