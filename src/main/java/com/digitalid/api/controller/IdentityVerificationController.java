package com.digitalid.api.controller;

import com.digitalid.api.service.IdentityVerificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/verify-identity", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;

    public IdentityVerificationController(IdentityVerificationService identityVerificationService) {
        this.identityVerificationService = identityVerificationService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(Authentication auth) {
        return ResponseEntity.ok(identityVerificationService.getStatus(auth.getName()));
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submit(
            Authentication auth,
            @RequestParam("idType") String idType,
            @RequestParam("frontFile") MultipartFile frontFile,
            @RequestParam(value = "backFile", required = false) MultipartFile backFile,
            @RequestParam("selfieFile") MultipartFile selfieFile) throws IOException {

        Map<String, Object> result = identityVerificationService.submit(
                auth.getName(), idType, frontFile, backFile, selfieFile);
        return ResponseEntity.ok(result);
    }
}
