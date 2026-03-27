package com.digitalid.api.controller;

import com.digitalid.api.controller.models.DeveloperApp;
import com.digitalid.api.service.VerificationGrantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public verification endpoint for third-party apps.
 * Authentication is handled by ApiKeyFilter (X-API-Key header), not JWT.
 */
@RestController
@RequestMapping(value = "/api/verify", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicVerifyController {

    private final VerificationGrantService grantService;

    public PublicVerifyController(VerificationGrantService grantService) {
        this.grantService = grantService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> verify(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {

        // App is set by ApiKeyFilter after successful API key validation
        DeveloperApp app = (DeveloperApp) request.getAttribute("authenticatedApp");
        if (app == null)
            return ResponseEntity.status(401).body(Map.of("error", "X-API-Key header is required"));

        String token = body.get("token");
        String credentialType = body.get("credential_type");

        if (token == null || token.isBlank() || credentialType == null || credentialType.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "token and credential_type are required"));

        return ResponseEntity.ok(grantService.redeem(app, token, credentialType));
    }
}
