package com.digitalid.api.controller;

import com.digitalid.api.service.ConnectivityCheckService;
import com.digitalid.api.service.email.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestApiController {

    private final ConnectivityCheckService connectivityCheckService;
    private final EmailService emailService;

    public TestApiController(ConnectivityCheckService connectivityCheckService, EmailService emailService) {
        this.connectivityCheckService = connectivityCheckService;
        this.emailService = emailService;
    }

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> testHello(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Unauthenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + authentication.getName() + ", you are authenticated!"
        ));
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, String>> profile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "message", "This is your profile"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Check connectivity to all configured dependencies (MySQL, Redis, Kafka).
     * Returns UP, DOWN, or DISABLED per component. No auth required for ops/readiness checks.
     */
    @GetMapping("/connectivity")
    public ResponseEntity<Map<String, Object>> connectivity() {
        Map<String, Object> body = new HashMap<>(Map.of(
                "timestamp", Instant.now().toString()
        ));
        body.put("connections", connectivityCheckService.checkAll());
        return ResponseEntity.ok(body);
    }

    /**
     * Check SMTP connectivity only (no email sent).
     * GET /test/smtp
     */
    @GetMapping("/smtp")
    public ResponseEntity<Map<String, Object>> smtpCheck() {
        Map<String, Object> result = new HashMap<>(connectivityCheckService.checkSmtp());
        result.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    /**
     * Send a test email to verify full end-to-end email delivery.
     * POST /test/email          → sends to sivatarunreddy00@gmail.com (default)
     * POST /test/email?to=x@y   → sends to the given address
     */
    @PostMapping("/email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(
            @RequestParam(defaultValue = "sivatarunreddy00@gmail.com") String to) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("to", to);
        try {
            emailService.sendTestEmail(to);
            result.put("status", "SENT");
            result.put("message", "Test email delivered successfully to " + to);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "FAILED");
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            result.put("message", cause.getMessage() != null ? cause.getMessage() : e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> context(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Unauthenticated"));
        }

        return ResponseEntity.ok(Map.of(
                "name", authentication.getName(),
                "principal", authentication.getPrincipal(),
                "authorities", authentication.getAuthorities()
        ));
    }
}

