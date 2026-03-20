package com.digitalid.api.controller;

import com.digitalid.api.audit.AuditLog;
import com.digitalid.api.audit.AuditLogRepository;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.controller.models.VerificationStatus;
import com.digitalid.api.repositroy.*;
import com.digitalid.api.service.CredentialService;
import com.digitalid.api.service.IdentityVerificationService;
import com.digitalid.api.service.ServiceConnectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
public class DashboardController {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ServiceConnectionService serviceConnectionService;
    private final CredentialService credentialService;
    private final IdentityVerificationService identityVerificationService;
    private final AuditLogRepository auditLogRepository;

    public DashboardController(UserRepository userRepository,
                                DocumentRepository documentRepository,
                                ServiceConnectionService serviceConnectionService,
                                CredentialService credentialService,
                                IdentityVerificationService identityVerificationService,
                                AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.serviceConnectionService = serviceConnectionService;
        this.credentialService = credentialService;
        this.identityVerificationService = identityVerificationService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        long docCount     = documentRepository.findByUser_IdOrderByUploadedAtDesc(user.getId()).size();
        long serviceCount = serviceConnectionService.countConnected(user.getId());
        long credCount    = credentialService.countVerified(user.getId());
        boolean identityVerified = identityVerificationService.isVerified(user.getId());

        // Last 5 audit log entries
        List<AuditLog> recent = auditLogRepository.findByUsernameOrderByCreatedAtDesc(auth.getName());
        List<Map<String, Object>> recentActivity = recent.stream()
                .limit(5)
                .map(this::activityToMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docCount", docCount);
        result.put("serviceCount", serviceCount);
        result.put("credentialCount", credCount);
        result.put("identityVerified", identityVerified);
        result.put("recentActivity", recentActivity);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/wallet")
    public ResponseEntity<Map<String, Object>> getWallet(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean identityVerified = identityVerificationService.isVerified(user.getId());
        List<Map<String, Object>> credentials = credentialService.getForWallet(user.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", user.getName());
        result.put("userId", user.getId());
        result.put("identityVerified", identityVerified);
        result.put("credentials", credentials);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> activityToMap(AuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", mapToType(log.getAction().name()));
        m.put("label", mapToTitle(log.getAction().name()));
        m.put("detail", log.getDetails() != null ? log.getDetails() : "");
        m.put("time", log.getCreatedAt().toString());
        return m;
    }

    private String mapToType(String action) {
        return switch (action) {
            case "LOGIN", "LOGIN_FAILED", "LOGOUT", "REGISTER" -> "login";
            case "PASSWORD_CHANGE", "PASSWORD_RESET_REQUEST", "PASSWORD_RESET", "PROFILE_UPDATE" -> "security";
            case "DOCUMENT_UPLOAD", "DOCUMENT_DELETE", "IDENTITY_VERIFY_SUBMITTED" -> "doc";
            case "SERVICE_CONNECTED", "SERVICE_DISCONNECTED" -> "service";
            case "CREDENTIAL_VERIFY_STARTED" -> "credential";
            default -> "security";
        };
    }

    private String mapToTitle(String action) {
        return switch (action) {
            case "LOGIN"                    -> "Signed in";
            case "LOGIN_FAILED"             -> "Sign in failed";
            case "LOGOUT"                   -> "Signed out";
            case "REGISTER"                 -> "Account created";
            case "PASSWORD_CHANGE"          -> "Password changed";
            case "PROFILE_UPDATE"           -> "Profile updated";
            case "DOCUMENT_UPLOAD"          -> "Document uploaded";
            case "DOCUMENT_DELETE"          -> "Document removed";
            case "SERVICE_CONNECTED"        -> "Service connected";
            case "SERVICE_DISCONNECTED"     -> "Service disconnected";
            case "CREDENTIAL_VERIFY_STARTED"-> "Credential verification started";
            case "IDENTITY_VERIFY_SUBMITTED"-> "Identity verification submitted";
            default -> action.replace("_", " ").toLowerCase();
        };
    }
}
