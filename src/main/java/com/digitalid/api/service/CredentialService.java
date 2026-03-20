package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.controller.models.UserCredential;
import com.digitalid.api.controller.models.VerificationStatus;
import com.digitalid.api.repositroy.IdentityVerificationRepository;
import com.digitalid.api.repositroy.UserCredentialRepository;
import com.digitalid.api.repositroy.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CredentialService {

    private static final Set<String> AVAILABLE_TYPES = Set.of(
            "military", "student", "first_responder", "teacher",
            "healthcare", "government", "senior"
    );

    private final UserCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public CredentialService(UserCredentialRepository credentialRepository,
                              UserRepository userRepository,
                              IdentityVerificationRepository identityVerificationRepository,
                              NotificationService notificationService,
                              AuditLogService auditLogService) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.identityVerificationRepository = identityVerificationRepository;
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
