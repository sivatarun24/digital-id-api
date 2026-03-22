package com.digitalid.api.controller;

import com.digitalid.api.audit.AuditLog;
import com.digitalid.api.audit.AuditLogRepository;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/activity", produces = MediaType.APPLICATION_JSON_VALUE)
public class ActivityController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public ActivityController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @SuppressWarnings("unchecked")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getActivity(
            Authentication auth,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        String username = auth.getName();
        List<AuditLog> logs = auditLogRepository.findByUsernameOrderByCreatedAtDesc(username);

        if (type != null && !type.isBlank() && !type.equals("all")) {
            String filterType = type.toLowerCase();
            logs = logs.stream()
                    .filter(l -> mapToActivityType(l.getAction().name()).equals(filterType))
                    .collect(Collectors.toList());
        }

        int total = logs.size();
        List<Map<String, Object>> page = logs.stream()
                .skip(offset).limit(limit)
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "activity", page,
                "total", total,
                "hasMore", (offset + limit) < total
        ));
    }

    private Map<String, Object> toMap(AuditLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("type", mapToActivityType(log.getAction().name()));
        m.put("title", mapToTitle(log.getAction().name()));
        m.put("desc", log.getDetails() != null ? log.getDetails() : "");
        m.put("ip", log.getIpAddress());
        m.put("userAgent", log.getUserAgent());
        m.put("timestamp", log.getCreatedAt().toString());
        return m;
    }

    private String mapToActivityType(String action) {
        return switch (action) {
            case "LOGIN", "LOGIN_FAILED", "LOGOUT", "REGISTER", "TOKEN_REFRESH" -> "login";
            case "PASSWORD_CHANGE", "PASSWORD_RESET_REQUEST", "PASSWORD_RESET" -> "security";
            case "DOCUMENT_UPLOAD", "DOCUMENT_DELETE", "IDENTITY_VERIFY_SUBMITTED" -> "verification";
            case "SERVICE_CONNECTED", "SERVICE_DISCONNECTED" -> "service";
            case "CREDENTIAL_VERIFY_STARTED" -> "credential";
            case "EMAIL_SENT", "EMAIL_FAILED", "PROFILE_UPDATE" -> "security";
            default -> "security";
        };
    }

    private String mapToTitle(String action) {
        return switch (action) {
            case "LOGIN"                    -> "Sign in successful";
            case "LOGIN_FAILED"             -> "Sign in failed";
            case "LOGOUT"                   -> "Signed out";
            case "REGISTER"                 -> "Account created";
            case "PASSWORD_CHANGE"          -> "Password changed";
            case "PASSWORD_RESET_REQUEST"   -> "Password reset requested";
            case "PASSWORD_RESET"           -> "Password reset";
            case "PROFILE_UPDATE"           -> "Profile updated";
            case "TOKEN_REFRESH"            -> "Session refreshed";
            case "EMAIL_SENT"               -> "Email sent";
            case "EMAIL_FAILED"             -> "Email delivery failed";
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
