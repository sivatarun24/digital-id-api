package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.Document;
import com.digitalid.api.controller.models.DocumentStatus;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.DocumentRepository;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository     userRepository;
    private final NotificationService notificationService;
    private final AuditLogService    auditLogService;
    private final StorageService     storageService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           AuditLogService auditLogService,
                           StorageService storageService) {
        this.documentRepository  = documentRepository;
        this.userRepository      = userRepository;
        this.notificationService = notificationService;
        this.auditLogService     = auditLogService;
        this.storageService      = storageService;
    }

    public List<Map<String, Object>> getDocuments(String username) {
        User user = getUser(username);
        List<Document> docs = documentRepository.findByUser_IdOrderByUploadedAtDesc(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Document doc : docs) result.add(toMap(doc));
        return result;
    }

    public Map<String, Object> uploadDocument(
            String username,
            String documentType,
            String issuer,
            String expiresAt,
            MultipartFile file) throws IOException {

        User user = getUser(username);

        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        if (documentType == null || documentType.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");

        String mimeType = file.getContentType();
        if (mimeType == null || (!mimeType.startsWith("image/") && !mimeType.equals("application/pdf")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only images and PDFs are allowed");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size must not exceed 10 MB");

        // Sequential number: how many of this type the user already has + 1
        int seq = documentRepository.countByUser_IdAndDocumentType(user.getId(), documentType.trim()) + 1;

        String storedPath = storageService.store(
                user.getId(), documentType.trim(), seq, file.getOriginalFilename(), file);

        Document doc = Document.builder()
                .user(user)
                .documentType(documentType.trim())
                .issuer(issuer != null && !issuer.isBlank() ? issuer.trim() : null)
                .status(DocumentStatus.VERIFIED)
                .originalFileName(file.getOriginalFilename())
                .filePath(storedPath)
                .fileSize(file.getSize())
                .mimeType(mimeType)
                .expiresAt(expiresAt != null && !expiresAt.isBlank() ? LocalDate.parse(expiresAt) : null)
                .build();

        doc = documentRepository.save(doc);

        notificationService.create(user.getId(), "verification",
                "Document uploaded",
                "Your " + documentType.replace("_", " ") + " has been uploaded and is now available in your documents.");
        auditLogService.log(username, AuditAction.DOCUMENT_UPLOAD,
                documentType + ": " + file.getOriginalFilename());

        return toMap(doc);
    }

    public void deleteDocument(String username, Long documentId) {
        User user = getUser(username);
        Document doc = documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        storageService.delete(doc.getFilePath());
        documentRepository.delete(doc);
        auditLogService.log(username, AuditAction.DOCUMENT_DELETE, doc.getDocumentType());
    }

    public Resource getDocumentFile(String username, Long documentId) {
        User user = getUser(username);
        Document doc = documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        try {
            return storageService.load(doc.getFilePath());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public Map<String, Object> replaceDocument(
            String username, Long documentId, MultipartFile file) throws IOException {

        User user = getUser(username);
        Document doc = documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");

        String mimeType = file.getContentType();
        if (mimeType == null || (!mimeType.startsWith("image/") && !mimeType.equals("application/pdf")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only images and PDFs are allowed");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size must not exceed 10 MB");

        // Delete old file then store replacement; use document ID as seq to avoid naming conflicts
        storageService.delete(doc.getFilePath());
        String newPath = storageService.store(
                user.getId(), doc.getDocumentType(), doc.getId().intValue(),
                file.getOriginalFilename(), file);

        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setFilePath(newPath);
        doc.setFileSize(file.getSize());
        doc.setMimeType(mimeType);
        doc.setStatus(DocumentStatus.VERIFIED);
        doc = documentRepository.save(doc);

        notificationService.create(user.getId(), "verification",
                "Document updated",
                "Your " + doc.getDocumentType().replace("_", " ") + " has been updated in your documents.");
        auditLogService.log(username, AuditAction.DOCUMENT_UPLOAD,
                doc.getDocumentType() + " (replaced): " + file.getOriginalFilename());

        return toMap(doc);
    }

    public String getDocumentMimeType(String username, Long documentId) {
        User user = getUser(username);
        return documentRepository.findByIdAndUser_Id(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"))
                .getMimeType();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Map<String, Object> toMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",               doc.getId());
        map.put("documentType",     doc.getDocumentType());
        map.put("issuer",           doc.getIssuer());
        map.put("status",           doc.getStatus().name().toLowerCase());
        map.put("originalFileName", doc.getOriginalFileName());
        map.put("fileSize",         doc.getFileSize());
        map.put("mimeType",         doc.getMimeType());
        map.put("expiresAt",        doc.getExpiresAt() != null ? doc.getExpiresAt().toString() : null);
        map.put("uploadedAt",       doc.getUploadedAt() != null ? doc.getUploadedAt().toLocalDate().toString() : null);
        return map;
    }
}
