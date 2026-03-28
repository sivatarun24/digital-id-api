package com.digitalid.api.controller;

import com.digitalid.api.service.CredentialService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
public class CredentialController {

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCredentials(Authentication auth) {
        return ResponseEntity.ok(credentialService.getCredentials(auth.getName()));
    }

    @PostMapping("/{credentialType}/start")
    public ResponseEntity<Map<String, Object>> startVerification(
            Authentication auth,
            @PathVariable String credentialType,
            @RequestParam Map<String, String> fields,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        fields.remove("file");
        return ResponseEntity.ok(credentialService.startVerification(auth.getName(), credentialType, fields, file));
    }

    @PostMapping(value = "/{credentialType}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submitDocument(
            Authentication auth,
            @PathVariable String credentialType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "verificationEmail", required = false) String verificationEmail) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        return ResponseEntity.ok(credentialService.submitDocument(auth.getName(), credentialType, file, verificationEmail));
    }

    @PostMapping("/{credentialType}/request-email-verification")
    public ResponseEntity<Map<String, Object>> requestEmailVerification(
            Authentication auth,
            @PathVariable String credentialType,
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        return ResponseEntity.ok(credentialService.requestEmailVerification(auth.getName(), credentialType, email));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmailToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        return ResponseEntity.ok(credentialService.verifyEmailToken(token));
    }
}
