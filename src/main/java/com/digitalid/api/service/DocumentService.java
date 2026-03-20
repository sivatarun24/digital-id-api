package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.Document;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.DocumentRepository;
import com.digitalid.api.repositroy.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    public DocumentService(DocumentRepository documentRepository, UserRepository userRepository,
                           NotificationService notificationService, AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    public List<Map<String, Object>> getDocuments(String username) {
        User user = getUser(username);
        List<Document> docs = documentRepository.findByUser_IdOrderByUploadedAtDesc(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : docs) {
            result.add(toMap(doc));
        }
        return result;
    }

    public Map<String, Object> uploadDocument(
            String username,
            String documentType,
            String issuer,
            String expiresAt,
            MultipartFile file) throws IOException {

        User user = getUser(username);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        if (documentType == null || documentType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || (!mimeType.startsWith("image/") && !mimeType.equals("application/pdf"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only images and PDFs are allowed");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size must not exceed 10 MB");
        }

        String ext = getExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + ext;
        Path userDir = Paths.get(uploadsDir, "documents", String.valueOf(user.getId()));
        Files.createDirectories(userDir);
        Path dest = userDir.resolve(storedName).toAbsolutePath();
        file.transferTo(dest.toFile());

        Document doc = Document.builder()
                .user(user)
                .documentType(documentType.trim())
                .issuer(issuer != null && !issuer.isBlank() ? issuer.trim() : null)
                .originalFileName(file.getOriginalFilename())
                .filePath(dest.toAbsolutePath().toString())
                .fileSize(file.getSize())
                .mimeType(mimeType)
                .expiresAt(expiresAt != null && !expiresAt.isBlank() ? LocalDate.parse(expiresAt) : null)
                .build();

        doc = documentRepository.save(doc);

        notificationService.create(user.getId(), "verification",
                "Document uploaded for review",
                "Your " + documentType.replace("_", " ") + " has been submitted for verification.");
        auditLogService.log(username, AuditAction.DOCUMENT_UPLOAD,
                documentType + ": " + file.getOriginalFilename());

        return toMap(doc);
    }

    public void deleteDocument(String username, Long documentId) {
        User user = getUser(username);
        Document doc = documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException ignored) {
            // File cleanup is best-effort; metadata is always removed
        }

        documentRepository.delete(doc);
        auditLogService.log(username, AuditAction.DOCUMENT_DELETE, doc.getDocumentType());
    }

    public Resource getDocumentFile(String username, Long documentId) {
        User user = getUser(username);
        Document doc = documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        try {
            Path path = Paths.get(doc.getFilePath());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public String getDocumentMimeType(String username, Long documentId) {
        User user = getUser(username);
        Document doc = documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return doc.getMimeType();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Map<String, Object> toMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("documentType", doc.getDocumentType());
        map.put("issuer", doc.getIssuer());
        map.put("status", doc.getStatus().name().toLowerCase());
        map.put("originalFileName", doc.getOriginalFileName());
        map.put("fileSize", doc.getFileSize());
        map.put("mimeType", doc.getMimeType());
        map.put("expiresAt", doc.getExpiresAt() != null ? doc.getExpiresAt().toString() : null);
        map.put("uploadedAt", doc.getUploadedAt() != null ? doc.getUploadedAt().toLocalDate().toString() : null);
        return map;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
