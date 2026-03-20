package com.digitalid.api.controller;

import com.digitalid.api.service.ConnectivityCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestApiController {

    private final ConnectivityCheckService connectivityCheckService;

    public TestApiController(ConnectivityCheckService connectivityCheckService) {
        this.connectivityCheckService = connectivityCheckService;
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

