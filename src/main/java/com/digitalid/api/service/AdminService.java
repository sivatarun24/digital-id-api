package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.*;
import com.digitalid.api.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final UserCredentialRepository credentialRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final CredentialService credentialService;
    private final InfoRequestService infoRequestService;

    public AdminService(UserRepository userRepository,
                        DocumentRepository documentRepository,
                        IdentityVerificationRepository verificationRepository,
                        UserCredentialRepository credentialRepository,
                        InstitutionRepository institutionRepository,
                        AuditLogService auditLogService,
                        PasswordEncoder passwordEncoder,
                        StorageService storageService,
                        NotificationService notificationService,
                        CredentialService credentialService,
                        InfoRequestService infoRequestService) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.verificationRepository = verificationRepository;
        this.credentialRepository = credentialRepository;
        this.institutionRepository = institutionRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
        this.notificationService = notificationService;
        this.credentialService = credentialService;
        this.infoRequestService = infoRequestService;
    }

    /** Verify the calling admin's password — throws 403 if wrong. */
    public void verifyAdminPassword(String adminUsername, String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin password is required for this action");
        }
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin not found"));
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            auditLogService.log(adminUsername, AuditAction.LOGIN_FAILED, "Wrong password on privileged admin action");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect admin password");
        }
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.countByRole(Role.USER));
        stats.put("totalAdmins", userRepository.countByRole(Role.ADMIN));
        stats.put("totalInstAdmins", userRepository.countByRole(Role.INST_ADMIN));
        stats.put("activeUsers", userRepository.countByAccountStatus(AccountStatus.ACTIVE));
        stats.put("disabledUsers", userRepository.countByAccountStatus(AccountStatus.DISABLED));
        stats.put("pendingVerifications", verificationRepository.countByStatus(VerificationStatus.PENDING));
        stats.put("pendingDocuments", documentRepository.countByStatus(DocumentStatus.PENDING));
        stats.put("totalInstitutions", institutionRepository.count());
        return stats;
    }

    // ── Users ────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> listUsers(String role, String status, String q) {
        List<User> users;
        if (role != null && !role.isBlank()) {
            try {
                users = userRepository.findByRoleOrderByCreatedAtDesc(Role.valueOf(role.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + role);
            }
        } else {
            users = userRepository.findAllByOrderByCreatedAtDesc();
        }

        if (status != null && !status.isBlank()) {
            AccountStatus accountStatus;
            try {
                accountStatus = AccountStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
            users = users.stream().filter(u -> u.getAccountStatus() == accountStatus).collect(Collectors.toList());
        }

        if (q != null && !q.isBlank()) {
            String lq = q.toLowerCase();
            users = users.stream().filter(u ->
                    (u.getName() != null && u.getName().toLowerCase().contains(lq)) ||
                    (u.getUsername() != null && u.getUsername().toLowerCase().contains(lq)) ||
                    (u.getEmail() != null && u.getEmail().toLowerCase().contains(lq))
            ).collect(Collectors.toList());
        }

        return users.stream().map(this::buildUserSummary).collect(Collectors.toList());
    }

    public Map<String, Object> getUserDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Map<String, Object> detail = buildUserSummary(user);

        // Include latest verification (full detail including file presence flags)
        verificationRepository.findTopByUserIdOrderBySubmittedAtDesc(id).ifPresent(v -> {
            detail.put("latestVerification", buildVerificationWithUser(v));
        });

        // Proactive Status Sync for documents based on credential status
        List<UserCredential> userCredentials = credentialRepository.findByUserIdOrderByStartedAtDesc(id);
        List<Document> docs = documentRepository.findByUser_IdOrderByUploadedAtDesc(id);
        
        boolean repaired = false;
        for (Document d : docs) {
            if (d.getStatus() == DocumentStatus.PENDING) {
                // Check if there is a verified credential of this type
                Optional<UserCredential> matchingCred = userCredentials.stream()
                        .filter(c -> c.getCredentialType().equals(d.getDocumentType()))
                        .findFirst();
                
                if (matchingCred.isPresent() && matchingCred.get().getStatus() == VerificationStatus.VERIFIED) {
                    d.setStatus(DocumentStatus.VERIFIED);
                    documentRepository.save(d);
                    repaired = true;
                } else if (matchingCred.isPresent() && matchingCred.get().getStatus() == VerificationStatus.REJECTED) {
                    d.setStatus(DocumentStatus.REJECTED);
                    documentRepository.save(d);
                    repaired = true;
                }
            }
        }
        
        if (repaired) {
            // Re-fetch to ensure summaries are correct
            docs = documentRepository.findByUser_IdOrderByUploadedAtDesc(id);
        }

        detail.put("documents", docs.stream().map(this::buildDocSummary).collect(Collectors.toList()));

        return detail;
    }

    public Map<String, Object> createUser(String adminUsername, Map<String, String> body) {
        String username = body.get("username");
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        if (username == null || username.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        if (password == null || password.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        if (userRepository.findByUsername(username).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        if (userRepository.findByEmail(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");

        Role role = Role.USER;
        if (body.get("role") != null && !body.get("role").isBlank()) {
            try { role = Role.valueOf(body.get("role").toUpperCase()); } catch (IllegalArgumentException e) {
                log.warn("[Admin] Unrecognised role '{}' — defaulting to USER", body.get("role"));
            }
        }
        AccountStatus status = AccountStatus.ACTIVE;

        User user = User.builder()
                .username(username)
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .passwordUpdatedAt(LocalDateTime.now())
                .role(role)
                .accountStatus(status)
                .emailVerifiedAt(LocalDateTime.now())
                .twoFactorEnabled(false)
                .marketingOptIn(false)
                .failedLoginAttempts(0)
                .build();

        if (body.get("phoneNo") != null && !body.get("phoneNo").isBlank()) {
            try { user.setPhoneNo(Long.parseLong(body.get("phoneNo"))); } catch (NumberFormatException e) {
                log.warn("[Admin] Invalid phoneNo '{}' — skipping", body.get("phoneNo"));
            }
        }
        if (role == Role.INST_ADMIN && body.get("institutionId") != null) {
            try { user.setInstitutionId(Long.parseLong(body.get("institutionId"))); } catch (NumberFormatException e) {
                log.warn("[Admin] Invalid institutionId '{}' — skipping", body.get("institutionId"));
            }
        }

        userRepository.save(user);
        auditLogService.log(adminUsername, AuditAction.ADMIN_USER_CREATED,
                "Created user: " + username + " (" + email + ") role=" + role);
        return buildUserSummary(user);
    }

    public Map<String, Object> updateUserStatus(Long id, String status, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        AccountStatus prev = user.getAccountStatus();
        try {
            user.setAccountStatus(AccountStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed: ACTIVE, INACTIVE, DISABLED");
        }
        userRepository.save(user);
        auditLogService.log(adminUsername, AuditAction.ADMIN_USER_STATUS_CHANGED,
                "User " + user.getUsername() + " status: " + prev + " → " + user.getAccountStatus());
        return Map.of("message", "User status updated", "status", user.getAccountStatus());
    }

    public Map<String, Object> updateUserRole(Long id, String role, Long institutionId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        try {
            Role newRole = Role.valueOf(role.toUpperCase());
            user.setRole(newRole);
            if (newRole == Role.INST_ADMIN) {
                if (institutionId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "institutionId required for INST_ADMIN role");
                }
                if (!institutionRepository.existsById(institutionId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found");
                }
                user.setInstitutionId(institutionId);
            } else {
                user.setInstitutionId(null);
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role. Allowed: USER, ADMIN, INST_ADMIN");
        }
        userRepository.save(user);
        return Map.of("message", "User role updated", "role", user.getRole());
    }

    public Map<String, Object> updateUserRole(Long id, String role, Long institutionId, String adminUsername) {
        Map<String, Object> result = updateUserRole(id, role, institutionId);
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            auditLogService.log(adminUsername, AuditAction.ADMIN_USER_ROLE_CHANGED,
                    "User " + user.getUsername() + " role set to " + role);
        }
        return result;
    }

    public void deleteCredential(Long id) {
        credentialService.deleteCredential(id);
    }

    @Transactional
    public Map<String, Object> deleteUser(Long id, String adminUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String info = user.getUsername() + " (" + user.getEmail() + ")";

        // 1. Delete Documents & Files
        List<Document> docs = documentRepository.findByUser_IdOrderByUploadedAtDesc(id);
        for (Document doc : docs) {
            if (doc.getFilePath() != null) {
                storageService.delete(doc.getFilePath());
            }
            documentRepository.delete(doc);
        }

        // 2. Delete Identity Verification & Files
        verificationRepository.findByUserIdOrderBySubmittedAtDesc(id).forEach(v -> {
            if (v.getSelfieFilePath() != null) storageService.delete(v.getSelfieFilePath());
            if (v.getFrontFilePath() != null) storageService.delete(v.getFrontFilePath());
            if (v.getBackFilePath() != null) storageService.delete(v.getBackFilePath());
            verificationRepository.delete(v);
        });

        // 3. Delete Credentials (using CredentialService for detail cleanup)
        credentialRepository.findByUserIdOrderByStartedAtDesc(id).forEach(c -> {
            credentialService.deleteCredential(c.getId());
        });

        // 4. Delete info requests & their files
        infoRequestService.deleteByUserId(id);

        // 5. Delete notifications
        notificationService.deleteByUserId(id);

        // 6. Delete the User
        userRepository.delete(user);

        auditLogService.log(adminUsername, AuditAction.ADMIN_USER_DELETED, "Deleted user and all associated data: " + info);
        return Map.of("message", "User and all associated data deleted");
    }

    // ── Verifications ────────────────────────────────────────────────────────

    public List<Map<String, Object>> listVerifications(String status) {
        List<IdentityVerification> list;
        if (status != null && !status.isBlank()) {
            try {
                list = verificationRepository.findByStatusOrderBySubmittedAtDesc(
                        VerificationStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        } else {
            list = verificationRepository.findAllByOrderBySubmittedAtDesc();
        }
        return list.stream().map(v -> buildVerificationWithUser(v)).collect(Collectors.toList());
    }

    public Map<String, Object> getVerification(Long id) {
        IdentityVerification v = verificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));
        return buildVerificationWithUser(v);
    }

    public Map<String, Object> reviewVerification(Long id, String status, String notes) {
        IdentityVerification v = verificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));
        VerificationStatus newStatus;
        try {
            newStatus = VerificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed: VERIFIED, REJECTED, PENDING");
        }
        v.setStatus(newStatus);
        v.setReviewedAt(LocalDateTime.now());
        v.setReviewerNotes(notes != null && !notes.isBlank() ? notes.trim() : null);
        verificationRepository.save(v);

        // Send notification to user
        String title, body;
        if (newStatus == VerificationStatus.VERIFIED) {
            title = "Identity Verification Approved";
            body = notes != null && !notes.isBlank()
                    ? "Your identity has been verified. Admin note: " + notes.trim()
                    : "Congratulations! Your identity has been successfully verified.";
        } else if (newStatus == VerificationStatus.REJECTED) {
            title = "Identity Verification Rejected";
            body = notes != null && !notes.isBlank()
                    ? "Your identity verification was not approved. Reason: " + notes.trim()
                    : "Your identity verification was not approved. Please resubmit with valid documents.";
        } else {
            title = "Identity Verification Reset";
            body = notes != null && !notes.isBlank()
                    ? "Your identity verification has been reset to pending. Note: " + notes.trim()
                    : "Your identity verification has been reset to pending review.";
        }
        notificationService.create(v.getUserId(), "verification", title, body);

        return Map.of("message", "Verification reviewed", "status", v.getStatus());
    }

    // ── Credentials ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCredentials(String status) {
        List<UserCredential> list = credentialRepository.findAllByOrderByStartedAtDesc();

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
    public Map<String, Object> reviewCredential(Long id, String status, String notes) {
        UserCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));

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
    public Resource getCredentialFile(Long id) {
        UserCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        Document doc = documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(
                        credential.getUserId(), credential.getCredentialType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document not found"));
        try {
            return storageService.load(doc.getFilePath());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public String getCredentialMimeType(Long id) {
        UserCredential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        return documentRepository.findTopByUser_IdAndDocumentTypeOrderByUploadedAtDesc(
                        credential.getUserId(), credential.getCredentialType())
                .map(Document::getMimeType)
                .orElse("application/octet-stream");
    }

    // ── Documents ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDocuments(String status) {
        List<Document> list;
        if (status != null && !status.isBlank()) {
            try {
                list = documentRepository.findByStatusOrderByUploadedAtDesc(
                        DocumentStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        } else {
            list = documentRepository.findAllByOrderByUploadedAtDesc();
        }
        return list.stream().map(this::buildDocWithUser).collect(Collectors.toList());
    }

    public Resource getDocumentFile(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
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

    public Map<String, Object> reviewDocument(Long id, String status) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        // Accept APPROVED as an alias for VERIFIED (frontend uses APPROVED)
        String normalised = "APPROVED".equalsIgnoreCase(status) ? "VERIFIED" : status.toUpperCase();
        try {
            doc.setStatus(DocumentStatus.valueOf(normalised));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed: PENDING, APPROVED, REJECTED");
        }
        documentRepository.save(doc);

        // Reciprocal Sync: If document is approved/rejected, update corresponding credential status
        credentialRepository.findByUserIdAndCredentialType(doc.getUser().getId(), doc.getDocumentType())
                .ifPresent(cred -> {
                    if (cred.getStatus() == VerificationStatus.PENDING) {
                        cred.setStatus(normalised.equals("VERIFIED") ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
                        cred.setReviewedAt(LocalDateTime.now());
                        cred.setVerifiedAt(normalised.equals("VERIFIED") ? LocalDateTime.now() : null);
                        cred.setReviewerNotes("Synced from manual document review: " + normalised);
                        credentialRepository.save(cred);
                    }
                });

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

    public Map<String, Object> deleteDocument(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
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

    // ── Institutions ─────────────────────────────────────────────────────────

    public List<Map<String, Object>> listInstitutions() {
        return institutionRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::buildInstitutionMap).collect(Collectors.toList());
    }

    public Map<String, Object> getInstitution(Long id) {
        Institution inst = institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        Map<String, Object> m = buildInstitutionMap(inst);
        // Include members list
        List<User> members = userRepository.findByInstitutionIdOrderByCreatedAtDesc(id);
        m.put("members", members.stream().map(this::buildUserSummary).collect(Collectors.toList()));
        return m;
    }

    public Map<String, Object> updateInstitutionPermissions(Long id, Map<String, Boolean> perms, String adminUsername) {
        Institution inst = institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        if (perms.get("allowVerifications") != null) inst.setAllowVerifications(perms.get("allowVerifications"));
        if (perms.get("allowDocuments") != null) inst.setAllowDocuments(perms.get("allowDocuments"));
        if (perms.get("canViewUsers") != null) inst.setCanViewUsers(perms.get("canViewUsers"));
        if (perms.get("canManageUsers") != null) inst.setCanManageUsers(perms.get("canManageUsers"));
        if (perms.get("canDeleteUsers") != null) inst.setCanDeleteUsers(perms.get("canDeleteUsers"));
        if (perms.get("canViewVerifications") != null) inst.setCanViewVerifications(perms.get("canViewVerifications"));
        if (perms.get("canManageVerifications") != null) inst.setCanManageVerifications(perms.get("canManageVerifications"));
        if (perms.get("canViewDocuments") != null) inst.setCanViewDocuments(perms.get("canViewDocuments"));
        if (perms.get("canManageDocuments") != null) inst.setCanManageDocuments(perms.get("canManageDocuments"));
        if (perms.get("canViewActivity") != null) inst.setCanViewActivity(perms.get("canViewActivity"));
        institutionRepository.save(inst);
        auditLogService.log(adminUsername, AuditAction.ADMIN_INSTITUTION_PERMISSIONS_CHANGED,
                "Institution " + inst.getName() + " permissions updated");
        return buildInstitutionMap(inst);
    }

    public Map<String, Object> createInstitution(String name, String code, String description,
                                                  String type, String website, String email,
                                                  String phone, String address, String city, String country,
                                                  String state, String pincode, String county,
                                                  String adminUsername) {
        if (institutionRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Institution name already exists");
        }
        if (code != null && !code.isBlank() && institutionRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Institution code already exists");
        }
        Institution inst = Institution.builder()
                .name(name)
                .code(code != null && !code.isBlank() ? code.toUpperCase() : null)
                .description(description)
                .type(type)
                .website(website)
                .email(email)
                .phone(phone)
                .address(address)
                .city(city)
                .country(country)
                .state(state)
                .pincode(pincode)
                .county(county)
                .build();
        institutionRepository.save(inst);
        auditLogService.log(adminUsername, AuditAction.ADMIN_INSTITUTION_CREATED,
                "Created institution: " + name);
        return buildInstitutionMap(inst);
    }

    public Map<String, Object> updateInstitution(Long id, String name, String code, String description,
                                                  String type, String website, String email,
                                                  String phone, String address, String city, String country,
                                                  String state, String pincode, String county) {
        Institution inst = institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        if (name != null && !name.isBlank()) inst.setName(name);
        if (code != null && !code.isBlank()) inst.setCode(code.toUpperCase());
        if (description != null) inst.setDescription(description);
        if (type != null) inst.setType(type);
        if (website != null) inst.setWebsite(website);
        if (email != null) inst.setEmail(email);
        if (phone != null) inst.setPhone(phone);
        if (address != null) inst.setAddress(address);
        if (city != null) inst.setCity(city);
        if (country != null) inst.setCountry(country);
        if (state != null) inst.setState(state);
        if (pincode != null) inst.setPincode(pincode);
        if (county != null) inst.setCounty(county);
        institutionRepository.save(inst);
        return buildInstitutionMap(inst);
    }

    public Map<String, Object> deleteInstitution(Long id, String adminUsername) {
        Institution inst = institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        String instName = inst.getName();
        List<User> members = userRepository.findByInstitutionIdOrderByCreatedAtDesc(id);
        members.forEach(u -> { u.setInstitutionId(null); if (u.getRole() == Role.INST_ADMIN) u.setRole(Role.USER); });
        userRepository.saveAll(members);
        institutionRepository.delete(inst);
        auditLogService.log(adminUsername, AuditAction.ADMIN_INSTITUTION_DELETED,
                "Deleted institution: " + instName + " (unlinked " + members.size() + " members)");
        return Map.of("message", "Institution deleted");
    }

    public Map<String, Object> assignInstAdmin(Long institutionId, Long userId) {
        institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(Role.INST_ADMIN);
        user.setInstitutionId(institutionId);
        userRepository.save(user);
        return Map.of("message", "User assigned as institutional admin", "userId", userId, "institutionId", institutionId);
    }

    public List<Map<String, Object>> getInstitutionMembers(Long institutionId) {
        institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
        return userRepository.findByInstitutionIdOrderByCreatedAtDesc(institutionId)
                .stream().map(this::buildUserSummary).collect(Collectors.toList());
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
        m.put("twoFactorEnabled", Boolean.TRUE.equals(u.getTwoFactorEnabled()));
        m.put("dateOfBirth", u.getDateOfBirth());
        m.put("gender", u.getGender());
        m.put("failedLoginAttempts", u.getFailedLoginAttempts());
        m.put("marketingOptIn", Boolean.TRUE.equals(u.getMarketingOptIn()));
        m.put("updatedAt", u.getUpdatedAt());
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

    public org.springframework.core.io.Resource getVerificationFile(Long id, String side) {
        IdentityVerification v = verificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification not found"));
        String path = switch (side.toLowerCase()) {
            case "front"  -> v.getFrontFilePath();
            case "back"   -> v.getBackFilePath();
            case "selfie" -> v.getSelfieFilePath();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid side. Use front, back, or selfie");
        };
        if (path == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not provided");
        try {
            return storageService.load(path);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public String getVerificationFileMimeType(Long id, String side) {
        // Detect mime type from stored file path extension
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

    private Map<String, Object> buildInstitutionMap(Institution i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("name", i.getName());
        m.put("code", i.getCode());
        m.put("description", i.getDescription());
        m.put("type", i.getType());
        m.put("website", i.getWebsite());
        m.put("email", i.getEmail());
        m.put("phone", i.getPhone());
        m.put("address", i.getAddress());
        m.put("city", i.getCity());
        m.put("state", i.getState());
        m.put("country", i.getCountry());
        m.put("pincode", i.getPincode());
        m.put("county", i.getCounty());
        m.put("allowVerifications", Boolean.TRUE.equals(i.getAllowVerifications()));
        m.put("allowDocuments", Boolean.TRUE.equals(i.getAllowDocuments()));
        m.put("canViewUsers", Boolean.TRUE.equals(i.getCanViewUsers()));
        m.put("canManageUsers", Boolean.TRUE.equals(i.getCanManageUsers()));
        m.put("canDeleteUsers", Boolean.TRUE.equals(i.getCanDeleteUsers()));
        m.put("canViewVerifications", Boolean.TRUE.equals(i.getCanViewVerifications()));
        m.put("canManageVerifications", Boolean.TRUE.equals(i.getCanManageVerifications()));
        m.put("canViewDocuments", Boolean.TRUE.equals(i.getCanViewDocuments()));
        m.put("canManageDocuments", Boolean.TRUE.equals(i.getCanManageDocuments()));
        m.put("canViewActivity", Boolean.TRUE.equals(i.getCanViewActivity()));
        m.put("createdAt", i.getCreatedAt());
        m.put("updatedAt", i.getUpdatedAt());
        m.put("memberCount", userRepository.findByInstitutionIdOrderByCreatedAtDesc(i.getId()).size());
        return m;
    }

    private Map<String, Object> buildDocWithUser(Document d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("documentType", d.getDocumentType());
        m.put("issuer", d.getIssuer());
        m.put("status", docStatusLabel(d.getStatus()));
        m.put("originalFileName", d.getOriginalFileName());
        m.put("fileSize", d.getFileSize());
        m.put("mimeType", d.getMimeType());
        m.put("uploadedAt", d.getUploadedAt());
        m.put("expiresAt", d.getExpiresAt());
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
}
