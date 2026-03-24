package com.digitalid.api.controller;

import com.digitalid.api.service.InstAdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/inst-admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class InstAdminController {

    private final InstAdminService instAdminService;

    public InstAdminController(InstAdminService instAdminService) {
        this.instAdminService = instAdminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(Authentication auth) {
        return ResponseEntity.ok(instAdminService.getStats(auth.getName()));
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getPermissions(Authentication auth) {
        return ResponseEntity.ok(instAdminService.getPermissions(auth.getName()));
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            Authentication auth,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(instAdminService.listUsers(auth.getName(), q));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserDetail(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(instAdminService.getUserDetail(auth.getName(), id));
    }

    // ── Verifications ────────────────────────────────────────────────────────

    @GetMapping("/verifications")
    public ResponseEntity<List<Map<String, Object>>> listVerifications(
            Authentication auth,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(instAdminService.listVerifications(auth.getName(), status));
    }

    @PutMapping(value = "/verifications/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reviewVerification(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(instAdminService.reviewVerification(auth.getName(), id, status, notes));
    }

    @GetMapping(value = "/verifications/{id}/files/{side}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<org.springframework.core.io.Resource> getVerificationFile(
            Authentication auth,
            @PathVariable Long id,
            @PathVariable String side) {
        org.springframework.core.io.Resource resource = instAdminService.getVerificationFile(auth.getName(), id, side);
        String mimeType = instAdminService.getVerificationFileMimeType(id, side);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // ── Credentials ─────────────────────────────────────────────────────────

    @GetMapping("/credentials")
    public ResponseEntity<List<Map<String, Object>>> listCredentials(
            Authentication auth,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(instAdminService.listCredentials(auth.getName(), status));
    }

    @PutMapping(value = "/credentials/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reviewCredential(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(instAdminService.reviewCredential(auth.getName(), id, status, notes));
    }

    @GetMapping(value = "/credentials/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<org.springframework.core.io.Resource> getCredentialFile(
            Authentication auth,
            @PathVariable Long id) {
        org.springframework.core.io.Resource resource = instAdminService.getCredentialFile(auth.getName(), id);
        String mimeType = instAdminService.getCredentialMimeType(auth.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType != null ? mimeType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // ── Documents ────────────────────────────────────────────────────────────

    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(
            Authentication auth,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(instAdminService.listDocuments(auth.getName(), status));
    }

    @PutMapping(value = "/documents/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reviewDocument(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        return ResponseEntity.ok(instAdminService.reviewDocument(auth.getName(), id, status));
    }

    @GetMapping(value = "/documents/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<org.springframework.core.io.Resource> getDocumentFile(
            Authentication auth,
            @PathVariable Long id) {
        org.springframework.core.io.Resource resource = instAdminService.getDocumentFile(auth.getName(), id);
        String mimeType = instAdminService.getDocumentMimeType(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType != null ? mimeType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(instAdminService.deleteDocument(auth.getName(), id));
    }
}
