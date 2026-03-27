package com.digitalid.api.controller;

import com.digitalid.api.controller.models.DeveloperApp;
import com.digitalid.api.service.DeveloperAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/developers", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeveloperAppController {

    private final DeveloperAppService developerAppService;

    public DeveloperAppController(DeveloperAppService developerAppService) {
        this.developerAppService = developerAppService;
    }

    /** Public — no auth needed. Returns API key ONCE. */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(201).body(developerAppService.register(
                body.get("name"),
                body.get("website"),
                body.get("description"),
                body.get("callbackUrl"),
                body.get("allowedCredentialTypes"),
                body.get("ownerEmail")
        ));
    }

    /** Requires X-API-Key header (handled by ApiKeyFilter → request attribute). */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(HttpServletRequest request) {
        DeveloperApp app = getAuthenticatedApp(request);
        return ResponseEntity.ok(developerAppService.getById(app.getId()));
    }

    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> update(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        String rawKey = request.getHeader("X-API-Key");
        return ResponseEntity.ok(developerAppService.update(
                rawKey,
                body.get("name"),
                body.get("website"),
                body.get("description"),
                body.get("callbackUrl"),
                body.get("allowedCredentialTypes")
        ));
    }

    @PostMapping("/me/regenerate-key")
    public ResponseEntity<Map<String, Object>> regenerateKey(HttpServletRequest request) {
        String rawKey = request.getHeader("X-API-Key");
        return ResponseEntity.ok(developerAppService.regenerateKey(rawKey));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deactivate(HttpServletRequest request) {
        String rawKey = request.getHeader("X-API-Key");
        return ResponseEntity.ok(developerAppService.deactivate(rawKey));
    }

    private DeveloperApp getAuthenticatedApp(HttpServletRequest request) {
        Object app = request.getAttribute("authenticatedApp");
        if (!(app instanceof DeveloperApp))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "API key required");
        return (DeveloperApp) app;
    }
}
