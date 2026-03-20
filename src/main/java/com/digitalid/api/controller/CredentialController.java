package com.digitalid.api.controller;

import com.digitalid.api.service.CredentialService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
            @PathVariable String credentialType) {
        return ResponseEntity.ok(credentialService.startVerification(auth.getName(), credentialType));
    }
}
