package com.digitalid.api.service;

import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.*;
import com.digitalid.api.service.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InstAdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final UserCredentialRepository credentialRepository;
    private final InstitutionRepository institutionRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final CredentialService credentialService;

    public InstAdminService(UserRepository userRepository,
                            DocumentRepository documentRepository,
                            IdentityVerificationRepository verificationRepository,
                            UserCredentialRepository credentialRepository,
                            InstitutionRepository institutionRepository,
                            StorageService storageService,
                            NotificationService notificationService,
                            CredentialService credentialService) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.verificationRepository = verificationRepository;
        this.credentialRepository = credentialRepository;
        this.institutionRepository = institutionRepository;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.credentialService = credentialService;
    }

    private User requireInstAdmin(String username) {
        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin not found"));
        if (admin.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No institution assigned");
        }
        return admin;
    }

    private Institution requireInstitution(Long id) {
        return institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
    }

    /**
     * Enforce a fine-grained permission flag. Throws 403 if not enabled.
     * Verification/document flags are also gated on the top-level allow flags.
     */
    private void checkPerm(Institution inst, String flag) {
        boolean allowed = switch (flag) {
            case "canViewUsers"           -> Boolean.TRUE.equals(inst.getCanViewUsers());
            case "canManageUsers"         -> Boolean.TRUE.equals(inst.getCanManageUsers());
            case "canDeleteUsers"         -> Boolean.TRUE.equals(inst.getCanDeleteUsers());
            case "canViewVerifications"   -> Boolean.TRUE.equals(inst.getAllowVerifications())
                                            && Boolean.TRUE.equals(inst.getCanViewVerifications());
            case "canManageVerifications" -> Boolean.TRUE.equals(inst.getAllowVerifications())
                                            && Boolean.TRUE.equals(inst.getCanManageVerifications());
            case "canViewDocuments"       -> Boolean.TRUE.equals(inst.getAllowDocuments())
                                            && Boolean.TRUE.equals(inst.getCanViewDocuments());
            case "canManageDocuments"     -> Boolean.TRUE.equals(inst.getAllowDocuments())
                                            && Boolean.TRUE.equals(inst.getCanManageDocuments());
            case "canViewActivity"        -> Boolean.TRUE.equals(inst.getCanViewActivity());
            default -> false;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "'" + flag + "' is not enabled for your institution");
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    public Map<String, Object> getPermissions(String adminUsername) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        return buildPermissionsMap(inst);
    }

    private Map<String, Object> buildPermissionsMap(Institution inst) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("canViewUsers",           Boolean.TRUE.equals(inst.getCanViewUsers()));
        p.put("canManageUsers",         Boolean.TRUE.equals(inst.getCanManageUsers()));
        p.put("canDeleteUsers",         Boolean.TRUE.equals(inst.getCanDeleteUsers()));
        p.put("canViewVerifications",   Boolean.TRUE.equals(inst.getAllowVerifications())
                                        && Boolean.TRUE.equals(inst.getCanViewVerifications()));
        p.put("canManageVerifications", Boolean.TRUE.equals(inst.getAllowVerifications())
                                        && Boolean.TRUE.equals(inst.getCanManageVerifications()));
        p.put("canViewDocuments",       Boolean.TRUE.equals(inst.getAllowDocuments())
                                        && Boolean.TRUE.equals(inst.getCanViewDocuments()));
        p.put("canManageDocuments",     Boolean.TRUE.equals(inst.getAllowDocuments())
                                        && Boolean.TRUE.equals(inst.getCanManageDocuments()));
        p.put("canViewActivity",        Boolean.TRUE.equals(inst.getCanViewActivity()));
        return p;
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    public Map<String, Object> getStats(String adminUsername) {
        User admin = requireInstAdmin(adminUsername);
        Long instId = admin.getInstitutionId();
        Institution inst = requireInstitution(instId);

        List<User> members = userRepository.findByInstitutionIdOrderByCreatedAtDesc(instId);
        List<Long> memberIds = members.stream().map(User::getId).collect(Collectors.toList());

        long pendingDocs = memberIds.isEmpty() ? 0 :
                documentRepository.findByUser_IdInOrderByUploadedAtDesc(memberIds)
                        .stream().filter(d -> d.getStatus() == DocumentStatus.PENDING).count();
        long pendingVerifs = memberIds.isEmpty() ? 0 :
                verificationRepository.findByUserIdInOrderBySubmittedAtDesc(memberIds)
                        .stream().filter(v -> v.getStatus() == VerificationStatus.PENDING).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("institutionId", instId);
        stats.put("institutionName", inst.getName());
        stats.put("totalMembers", members.size());
        stats.put("activeMembers", members.stream().filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE).count());
        stats.put("pendingDocuments", pendingDocs);
        stats.put("pendingVerifications", pendingVerifs);
        stats.put("permissions", buildPermissionsMap(inst));
        return stats;
    }

    // ── Users ────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> listUsers(String adminUsername, String q) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewUsers");

        List<User> members = userRepository.findByInstitutionIdOrderByCreatedAtDesc(admin.getInstitutionId());

        if (q != null && !q.isBlank()) {
            String lq = q.toLowerCase();
            members = members.stream().filter(u ->
                    (u.getName() != null && u.getName().toLowerCase().contains(lq)) ||
                    (u.getUsername() != null && u.getUsername().toLowerCase().contains(lq)) ||
                    (u.getEmail() != null && u.getEmail().toLowerCase().contains(lq))
            ).collect(Collectors.toList());
        }

        return members.stream().map(this::buildUserSummary).collect(Collectors.toList());
    }

    public Map<String, Object> getUserDetail(String adminUsername, Long userId) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewUsers");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!admin.getInstitutionId().equals(user.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not in your institution");
        }

        Map<String, Object> detail = buildUserSummary(user);

        verificationRepository.findTopByUserIdOrderBySubmittedAtDesc(userId).ifPresent(v -> {
            Map<String, Object> vMap = new HashMap<>();
            vMap.put("id", v.getId());
            vMap.put("idType", v.getIdType());
            vMap.put("status", v.getStatus());
            vMap.put("submittedAt", v.getSubmittedAt());
            vMap.put("reviewedAt", v.getReviewedAt());
            detail.put("latestVerification", vMap);
        });

        detail.put("documents", documentRepository.findByUser_IdOrderByUploadedAtDesc(userId)
                .stream().map(this::buildDocSummary).collect(Collectors.toList()));

        return detail;
    }

    // ── Verifications ────────────────────────────────────────────────────────

    public List<Map<String, Object>> listVerifications(String adminUsername, String status) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewVerifications");

        List<Long> memberIds = userRepository.findByInstitutionIdOrderByCreatedAtDesc(admin.getInstitutionId())
                .stream().map(User::getId).collect(Collectors.toList());

        if (memberIds.isEmpty()) return List.of();

        List<IdentityVerification> list = verificationRepository.findByUserIdInOrderBySubmittedAtDesc(memberIds);

        if (status != null && !status.isBlank()) {
            try {
                VerificationStatus vs = VerificationStatus.valueOf(status.toUpperCase());
                list = list.stream().filter(v -> v.getStatus() == vs).collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        }

        return list.stream().map(this::buildVerificationWithUser).collect(Collectors.toList());
    }

    public Map<String, Object> reviewVerification(String adminUsername, Long verificationId, String status, String notes) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canManageVerifications");

        IdentityVerification v = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));

        User owner = userRepository.findById(v.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!admin.getInstitutionId().equals(owner.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verification not in your institution");
        }

        VerificationStatus newStatus;
        try {
            newStatus = VerificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed: VERIFIED, REJECTED, PENDING");
        }
        v.setStatus(newStatus);
        v.setReviewedAt(LocalDateTime.now());
        if (notes != null && !notes.isBlank()) {
            v.setReviewerNotes(notes.trim());
        }
        verificationRepository.save(v);

        // Notify user
        String title, body;
        if (newStatus == VerificationStatus.VERIFIED) {
            title = "Identity Verification Approved";
            body = notes != null && !notes.isBlank()
                    ? "Your identity has been verified. Note: " + notes.trim()
                    : "Your identity has been successfully verified.";
        } else if (newStatus == VerificationStatus.REJECTED) {
            title = "Identity Verification Rejected";
            body = notes != null && !notes.isBlank()
                    ? "Your identity verification was not approved. Reason: " + notes.trim()
                    : "Your identity verification was not approved. Please resubmit with valid documents.";
        } else {
            title = "Identity Verification Reset";
            body = "Your identity verification has been reset to pending review.";
        }
        notificationService.create(v.getUserId(), "verification", title, body);

        return Map.of("message", "Verification reviewed", "status", v.getStatus());
    }

    public Resource getVerificationFile(String adminUsername, Long id, String side) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewVerifications");

        IdentityVerification v = verificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));
        User owner = userRepository.findById(v.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!admin.getInstitutionId().equals(owner.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verification not in your institution");
        }

        String path = switch (side.toLowerCase()) {
            case "front"  -> v.getFrontFilePath();
            case "back"   -> v.getBackFilePath();
            case "selfie" -> v.getSelfieFilePath();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid side. Use front, back, or selfie");
        };
        if (path == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not provided");
        try {
            return storageService.load(path);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public String getVerificationFileMimeType(Long id, String side) {
        IdentityVerification v = verificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));
        String path = switch (side.toLowerCase()) {
            case "front"  -> v.getFrontFilePath();
            case "back"   -> v.getBackFilePath();
            case "selfie" -> v.getSelfieFilePath();
            default -> null;
        };
        if (path == null) return "application/octet-stream";
        String lower = path.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ── Credentials ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCredentials(String adminUsername, String status) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewVerifications");

        List<Long> memberIds = userRepository.findByInstitutionIdOrderByCreatedAtDesc(admin.getInstitutionId())
                .stream().map(User::getId).collect(Collectors.toList());

        if (memberIds.isEmpty()) return List.of();

        List<UserCredential> list = credentialRepository.findByUserIdInOrderByStartedAtDesc(memberIds);

        if (status != null && !status.isBlank()) {
            try {
                VerificationStatus credentialStatus = VerificationStatus.valueOf(status.toUpperCase());
                list = list.stream().filter(c -> c.getStatus() == credentialStatus).collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        }

        return list.stream().map(this::buildCredentialWithUser).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> reviewCredential(String adminUsername, Long credentialId, String status, String notes) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canManageVerifications");

        UserCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));

        User owner = userRepository.findById(credential.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!admin.getInstitutionId().equals(owner.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credential not in your institution");
        }

        VerificationStatus newStatus;
        try {
            newStatus = VerificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed: VERIFIED, REJECTED, PENDING");
        }

        credential.setStatus(newStatus);
        credential.setReviewedAt(LocalDateTime.now());
        credential.setVerifiedAt(newStatus == VerificationStatus.VERIFIED ? LocalDateTime.now() : null);
        credential.setReviewerNotes(notes != null && !notes.isBlank() ? notes.trim() : null);
        credentialRepository.save(credential);

        documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(
                credential.getUserId(), credential.getCredentialType()
        ).ifPresent(doc -> {
            doc.setStatus(switch (newStatus) {
                case VERIFIED -> DocumentStatus.VERIFIED;
                case REJECTED -> DocumentStatus.REJECTED;
                case PENDING -> DocumentStatus.PENDING;
            });
            documentRepository.save(doc);
        });

        String label = credential.getCredentialType().replace("_", " ");
        String title;
        String body;
        if (newStatus == VerificationStatus.VERIFIED) {
            title = "Credential Approved";
            body = notes != null && !notes.isBlank()
                    ? "Your " + label + " credential was approved. Note: " + notes.trim()
                    : "Your " + label + " credential was approved.";
        } else if (newStatus == VerificationStatus.REJECTED) {
            title = "Credential Rejected";
            body = notes != null && !notes.isBlank()
                    ? "Your " + label + " credential was rejected. Reason: " + notes.trim()
                    : "Your " + label + " credential was rejected. Please resubmit clearer supporting documents.";
        } else {
            title = "Credential Reset";
            body = notes != null && !notes.isBlank()
                    ? "Your " + label + " credential was reset to pending review. Note: " + notes.trim()
                    : "Your " + label + " credential was reset to pending review.";
        }
        notificationService.create(credential.getUserId(), "verification", title, body);

        return Map.of("message", "Credential reviewed", "status", credential.getStatus());
    }

    @Transactional(readOnly = true)
    public Resource getCredentialFile(String adminUsername, Long credentialId) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewVerifications");

        UserCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));

        User owner = userRepository.findById(credential.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!admin.getInstitutionId().equals(owner.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credential not in your institution");
        }

        Document doc = documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(
                        credential.getUserId(), credential.getCredentialType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document not found"));
        try {
            return storageService.load(doc.getFilePath());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public String getCredentialMimeType(String adminUsername, Long credentialId) {
        User admin = requireInstAdmin(adminUsername);
        UserCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        User owner = userRepository.findById(credential.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!admin.getInstitutionId().equals(owner.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credential not in your institution");
        }
        return documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(
                        credential.getUserId(), credential.getCredentialType())
                .map(Document::getMimeType)
                .orElse("application/octet-stream");
    }

    // ── Documents ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDocuments(String adminUsername, String status) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewDocuments");

        List<Long> memberIds = userRepository.findByInstitutionIdOrderByCreatedAtDesc(admin.getInstitutionId())
                .stream().map(User::getId).collect(Collectors.toList());

        if (memberIds.isEmpty()) return List.of();

        List<Document> list = documentRepository.findByUser_IdInOrderByUploadedAtDesc(memberIds);

        if (status != null && !status.isBlank()) {
            // Normalize APPROVED → VERIFIED for DB comparison
            String normalised = "APPROVED".equalsIgnoreCase(status) ? "VERIFIED" : status.toUpperCase();
            try {
                DocumentStatus ds = DocumentStatus.valueOf(normalised);
                list = list.stream().filter(d -> d.getStatus() == ds).collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        }

        return list.stream().map(this::buildDocWithUser).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> reviewDocument(String adminUsername, Long documentId, String status) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canManageDocuments");

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!admin.getInstitutionId().equals(doc.getUser().getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document not in your institution");
        }

        String normalised = "APPROVED".equalsIgnoreCase(status) ? "VERIFIED" : status.toUpperCase();
        try {
            doc.setStatus(DocumentStatus.valueOf(normalised));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed: PENDING, APPROVED, REJECTED");
        }
        documentRepository.save(doc);

        // Notify user
        String title, body;
        if (doc.getStatus() == DocumentStatus.VERIFIED) {
            title = "Document Approved";
            body = "Your " + doc.getDocumentType().replace("_", " ") + " has been approved.";
        } else if (doc.getStatus() == DocumentStatus.REJECTED) {
            title = "Document Rejected";
            body = "Your " + doc.getDocumentType().replace("_", " ") + " was rejected. Please resubmit a clearer or valid document.";
        } else {
            title = "Document Reset to Pending";
            body = "Your " + doc.getDocumentType().replace("_", " ") + " has been reset to pending review.";
        }
        notificationService.create(doc.getUser().getId(), "verification", title, body);

        return Map.of("message", "Document reviewed", "status", docStatusLabel(doc.getStatus()));
    }

    @Transactional(readOnly = true)
    public Resource getDocumentFile(String adminUsername, Long id) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canViewDocuments");

        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!admin.getInstitutionId().equals(doc.getUser().getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document not in your institution");
        }
        try {
            return storageService.load(doc.getFilePath());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public String getDocumentMimeType(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"))
                .getMimeType();
    }

    public Map<String, Object> deleteDocument(String adminUsername, Long id) {
        User admin = requireInstAdmin(adminUsername);
        Institution inst = requireInstitution(admin.getInstitutionId());
        checkPerm(inst, "canManageDocuments");

        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!admin.getInstitutionId().equals(doc.getUser().getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document not in your institution");
        }
        if (doc.getStatus() != DocumentStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only rejected documents can be deleted");
        }

        storageService.delete(doc.getFilePath());
        documentRepository.delete(doc);
        return Map.of("message", "Document deleted");
    }

    /** Translates VERIFIED → APPROVED for API responses (frontend uses APPROVED). */
    private String docStatusLabel(DocumentStatus s) {
        return s == DocumentStatus.VERIFIED ? "APPROVED" : s.name();
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildUserSummary(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phoneNo", u.getPhoneNo());
        m.put("role", u.getRole());
        m.put("accountStatus", u.getAccountStatus());
        m.put("institutionId", u.getInstitutionId());
        m.put("emailVerifiedAt", u.getEmailVerifiedAt());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    private Map<String, Object> buildDocSummary(Document d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("documentType", d.getDocumentType());
        m.put("issuer", d.getIssuer());
        m.put("status", docStatusLabel(d.getStatus()));
        m.put("originalFileName", d.getOriginalFileName());
        m.put("fileSize", d.getFileSize());
        m.put("mimeType", d.getMimeType());
        m.put("uploadedAt", d.getUploadedAt());
        m.put("expiresAt", d.getExpiresAt());
        return m;
    }

    private Map<String, Object> buildDocWithUser(Document d) {
        Map<String, Object> m = buildDocSummary(d);
        if (d.getUser() != null) {
            m.put("userId", d.getUser().getId());
            m.put("userName", d.getUser().getName());
            m.put("userEmail", d.getUser().getEmail());
            m.put("username", d.getUser().getUsername());
            m.put("institutionId", d.getUser().getInstitutionId());
            if (d.getUser().getInstitutionId() != null) {
                institutionRepository.findById(d.getUser().getInstitutionId())
                        .ifPresent(inst -> m.put("institutionName", inst.getName()));
            }
        }
        return m;
    }

    private Map<String, Object> buildVerificationWithUser(IdentityVerification v) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", v.getId());
        m.put("userId", v.getUserId());
        m.put("idType", v.getIdType());
        m.put("status", v.getStatus());
        m.put("submittedAt", v.getSubmittedAt());
        m.put("reviewedAt", v.getReviewedAt());
        m.put("reviewerNotes", v.getReviewerNotes());
        m.put("hasFrontFile", v.getFrontFilePath() != null);
        m.put("hasBackFile", v.getBackFilePath() != null);
        m.put("hasSelfieFile", v.getSelfieFilePath() != null);
        userRepository.findById(v.getUserId()).ifPresent(u -> {
            m.put("userName", u.getName());
            m.put("userEmail", u.getEmail());
            m.put("username", u.getUsername());
        });
        return m;
    }

    private Map<String, Object> buildCredentialWithUser(UserCredential c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("userId", c.getUserId());
        m.put("credentialType", c.getCredentialType());
        m.put("status", c.getStatus());
        m.put("submittedAt", c.getStartedAt());
        m.put("reviewedAt", c.getReviewedAt());
        m.put("reviewerNotes", c.getReviewerNotes());
        m.put("fields", credentialService.getCredentialFields(c));

        documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(c.getUserId(), c.getCredentialType())
                .ifPresent(doc -> {
                    m.put("documentId", doc.getId());
                    m.put("documentStatus", doc.getStatus());
                    m.put("originalFileName", doc.getOriginalFileName());
                    m.put("mimeType", doc.getMimeType());
                });

        userRepository.findById(c.getUserId()).ifPresent(u -> {
            m.put("userName", u.getName());
            m.put("userEmail", u.getEmail());
            m.put("username", u.getUsername());
        });
        return m;
    }
}
