package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VerificationGrantService {

    private static final int TOKEN_EXPIRY_MINUTES = 10;

    private final VerificationGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final DeveloperAppRepository appRepository;
    private final AuditLogService auditLogService;       // reusing existing
    private final NotificationService notificationService; // reusing existing

    public VerificationGrantService(VerificationGrantRepository grantRepository,
                                    UserRepository userRepository,
                                    UserCredentialRepository credentialRepository,
                                    DeveloperAppRepository appRepository,
                                    AuditLogService auditLogService,
                                    NotificationService notificationService) {
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.appRepository = appRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    // ── Consent page info ────────────────────────────────────────────────────

    public Map<String, Object> getConsentInfo(Long appId, String credentialType) {
        DeveloperApp app = appRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App not found"));
        if (!"ACTIVE".equals(app.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This app is not active");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", app.getId());
        result.put("appName", app.getName());
        result.put("appWebsite", app.getWebsite());
        result.put("credentialType", credentialType);
        result.put("message", "\"" + app.getName() + "\" wants to verify your " + credentialType + " credential.");
        return result;
    }

    // ── Issue token (user approves) ──────────────────────────────────────────

    public Map<String, Object> issueToken(String username, Long appId, String credentialType) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        DeveloperApp app = appRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App not found"));

        if (!"ACTIVE".equals(app.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This app is not active");

        // Validate credential type is allowed for this app
        if (app.getAllowedCredentialTypes() != null &&
                !Arrays.asList(app.getAllowedCredentialTypes().split(",")).contains(credentialType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This app is not allowed to verify " + credentialType + " credentials");
        }

        VerificationGrant grant = VerificationGrant.builder()
                .userId(user.getId())
                .appId(appId)
                .credentialType(credentialType)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES))
                .used(false)
                .build();

        grantRepository.save(grant);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", grant.getToken());
        result.put("expiresAt", grant.getExpiresAt().toString());
        result.put("callbackUrl", app.getCallbackUrl() + "?token=" + grant.getToken());
        return result;
    }

    // ── Redeem token (third party calls /api/verify) ─────────────────────────

    public Map<String, Object> redeem(DeveloperApp app, String token, String credentialType) {
        Optional<VerificationGrant> optGrant = grantRepository.findByToken(token);

        if (optGrant.isEmpty() || !optGrant.get().getAppId().equals(app.getId())) {
            auditLogService.log("system", AuditAction.THIRD_PARTY_VERIFY_FAILED,
                    "App: " + app.getName() + " | Credential: " + credentialType + " | Reason: invalid token");
            return failResult("invalid");
        }

        VerificationGrant grant = optGrant.get();

        if (grant.isUsed()) {
            return failResult("invalid");
        }

        if (grant.getExpiresAt().isBefore(LocalDateTime.now())) {
            return failResult("expired");
        }

        if (!grant.getCredentialType().equals(credentialType)) {
            return failResult("invalid");
        }

        // Mark as used — single-use enforcement
        grant.setUsed(true);
        grantRepository.save(grant);

        // Look up the user's actual credential status (reusing existing UserCredentialRepository)
        Optional<UserCredential> credOpt = credentialRepository
                .findByUserIdAndCredentialType(grant.getUserId(), credentialType);

        if (credOpt.isEmpty() || credOpt.get().getStatus() != VerificationStatus.VERIFIED) {
            String reason = credOpt.isEmpty() ? "not_found"
                    : credOpt.get().getStatus().name().toLowerCase();

            // Audit log (reusing AuditLogService)
            userRepository.findById(grant.getUserId()).ifPresent(u ->
                    auditLogService.log(u.getUsername(), AuditAction.THIRD_PARTY_VERIFY_FAILED,
                            "App: " + app.getName() + " | Credential: " + credentialType + " | Result: " + reason));

            return failResult(reason);
        }

        UserCredential cred = credOpt.get();

        // Notify user (reusing NotificationService)
        notificationService.create(grant.getUserId(), "security",
                "Credential Verified",
                "\"" + app.getName() + "\" verified your " + credentialType + " credential.");

        // Audit log (reusing AuditLogService)
        userRepository.findById(grant.getUserId()).ifPresent(u ->
                auditLogService.log(u.getUsername(), AuditAction.THIRD_PARTY_VERIFY_SUCCESS,
                        "App: " + app.getName() + " | Credential: " + credentialType + " | Result: VERIFIED"));

        // Build privacy-safe response (no personal data)
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verified", true);
        result.put("credential_type", credentialType);
        result.put("verified_at", cred.getVerifiedAt() != null ? cred.getVerifiedAt().toString() : null);
        return result;
    }

    // ── User grant management ────────────────────────────────────────────────

    public List<Map<String, Object>> listGrants(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return grantRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(g -> !g.isUsed() && g.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.getId());
                    m.put("credentialType", g.getCredentialType());
                    m.put("appId", g.getAppId());
                    m.put("expiresAt", g.getExpiresAt().toString());
                    appRepository.findById(g.getAppId())
                            .ifPresent(a -> m.put("appName", a.getName()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> revokeGrant(String username, Long grantId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        VerificationGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grant not found"));

        if (!grant.getUserId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");

        grantRepository.delete(grant);
        return Map.of("message", "Grant revoked");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> failResult(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verified", false);
        result.put("reason", reason);
        return result;
    }
}
