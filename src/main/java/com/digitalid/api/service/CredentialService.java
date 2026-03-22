package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.*;
import com.digitalid.api.service.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CredentialService {

    private static final Set<String> AVAILABLE_TYPES = Set.of(
            "military", "student", "first_responder", "teacher",
            "healthcare", "government", "senior", "nonprofit"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    private final UserCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public CredentialService(UserCredentialRepository credentialRepository,
                              UserRepository userRepository,
                              IdentityVerificationRepository identityVerificationRepository,
                              DocumentRepository documentRepository,
                              StorageService storageService,
                              NotificationService notificationService,
                              AuditLogService auditLogService) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.identityVerificationRepository = identityVerificationRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    public List<Map<String, Object>> getCredentials(String username) {
        User user = getUser(username);
        return credentialRepository.findByUserId(user.getId())
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> startVerification(String username, String credentialType) {
        User user = getUser(username);

        if (!AVAILABLE_TYPES.contains(credentialType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credential type");
        }

        // Must have identity verification
        boolean identityVerified = identityVerificationRepository
                .existsByUserIdAndStatus(user.getId(), VerificationStatus.VERIFIED);
        if (!identityVerified) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Identity verification required before adding credentials");
        }

        if (credentialRepository.findByUserIdAndCredentialType(user.getId(), credentialType).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Credential already started or verified");
        }

        UserCredential cred = UserCredential.builder()
                .userId(user.getId())
                .credentialType(credentialType)
                .status(VerificationStatus.PENDING)
                .build();
        cred = credentialRepository.save(cred);

        notificationService.create(user.getId(), "verification",
                "Credential verification started",
                capitalize(credentialType.replace("_", " ")) + " affiliation verification is in progress.");
        auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED, credentialType);

        return toMap(cred);
    }

    public Map<String, Object> submitDocument(String username, String credentialType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supporting document is required");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP, and PDF files are accepted");
        }

        User user = getUser(username);

        UserCredential cred = credentialRepository.findByUserIdAndCredentialType(user.getId(), credentialType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Credential not started. Call /start first."));

        if (cred.getStatus() != VerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Document can only be submitted for credentials in PENDING status");
        }

        int seq = documentRepository.countByUser_IdAndDocumentType(user.getId(), credentialType) + 1;

        String storedPath;
        try {
            storedPath = storageService.store(user.getId(), credentialType, seq,
                    file.getOriginalFilename(), file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store document");
        }

        Document doc = Document.builder()
                .user(user)
                .documentType(credentialType)
                .originalFileName(file.getOriginalFilename())
                .filePath(storedPath)
                .fileSize(file.getSize())
                .mimeType(mime)
                .status(DocumentStatus.PENDING)
                .build();
        documentRepository.save(doc);

        notificationService.create(user.getId(), "verification",
                "Supporting document received",
                capitalize(credentialType.replace("_", " ")) + " supporting document submitted and under review.");
        auditLogService.log(username, AuditAction.CREDENTIAL_VERIFY_STARTED,
                credentialType + " document submitted");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Document submitted successfully. Your credential is under review.");
        result.put("credential", toMap(cred));
        return result;
    }

    public long countVerified(Long userId) {
        return credentialRepository.countByUserIdAndStatus(userId, VerificationStatus.VERIFIED);
    }

    public List<Map<String, Object>> getForWallet(Long userId) {
        return credentialRepository.findByUserId(userId)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    private Map<String, Object> toMap(UserCredential c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("credentialType", c.getCredentialType());
        m.put("status", c.getStatus().name().toLowerCase());
        m.put("startedAt", c.getStartedAt() != null ? c.getStartedAt().toLocalDate().toString() : null);
        m.put("verifiedAt", c.getVerifiedAt() != null ? c.getVerifiedAt().toLocalDate().toString() : null);
        return m;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
