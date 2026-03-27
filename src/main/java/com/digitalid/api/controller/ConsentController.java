package com.digitalid.api.controller;

import com.digitalid.api.service.VerificationGrantService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/consent", produces = MediaType.APPLICATION_JSON_VALUE)
public class ConsentController {

    private final VerificationGrantService grantService;

    public ConsentController(VerificationGrantService grantService) {
        this.grantService = grantService;
    }

    /** Show the user what the app is requesting — displayed on the consent page */
    @GetMapping("/request")
    public ResponseEntity<Map<String, Object>> getConsentInfo(
            @RequestParam("app_id") Long appId,
            @RequestParam("credential_type") String credentialType) {
        return ResponseEntity.ok(grantService.getConsentInfo(appId, credentialType));
    }

    /** User approves — issues a short-lived token and returns the callback redirect URL */
    @PostMapping(value = "/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> approve(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        Long appId = body.get("appId") != null
                ? Long.valueOf(body.get("appId").toString()) : null;
        String credentialType = (String) body.get("credentialType");
        if (appId == null || credentialType == null || credentialType.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "appId and credentialType are required"));
        return ResponseEntity.ok(grantService.issueToken(auth.getName(), appId, credentialType));
    }

    /** List all active (unexpired, unused) grants for the current user */
    @GetMapping("/grants")
    public ResponseEntity<List<Map<String, Object>>> listGrants(Authentication auth) {
        return ResponseEntity.ok(grantService.listGrants(auth.getName()));
    }

    /** Revoke a grant (user withdraws consent) */
    @DeleteMapping("/grants/{id}")
    public ResponseEntity<Map<String, Object>> revokeGrant(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(grantService.revokeGrant(auth.getName(), id));
    }
}
