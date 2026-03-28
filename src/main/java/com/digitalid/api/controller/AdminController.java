package com.digitalid.api.controller;

import com.digitalid.api.service.AdminService;
import com.digitalid.api.service.DeveloperAppService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminController {

    private final AdminService adminService;
    private final DeveloperAppService developerAppService;

    public AdminController(AdminService adminService, DeveloperAppService developerAppService) {
        this.adminService = adminService;
        this.developerAppService = developerAppService;
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(adminService.listUsers(role, status, q));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @PostMapping(value = "/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, String> body,
            Principal principal) {
        return ResponseEntity.status(201).body(adminService.createUser(principal.getName(), body));
    }

    @PutMapping(value = "/users/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal principal) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        return ResponseEntity.ok(adminService.updateUserStatus(id, status, principal.getName()));
    }

    @PutMapping(value = "/users/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Principal principal) {
        String role = body != null ? (String) body.get("role") : null;
        Long institutionId = body != null && body.get("institutionId") != null
                ? Long.valueOf(body.get("institutionId").toString()) : null;
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "role is required"));
        }
        return ResponseEntity.ok(adminService.updateUserRole(id, role, institutionId, principal.getName()));
    }

    @DeleteMapping(value = "/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        adminService.verifyAdminPassword(principal.getName(), body != null ? body.get("adminPassword") : null);
        return ResponseEntity.ok(adminService.deleteUser(id, principal.getName()));
    }

    // ── Verifications ────────────────────────────────────────────────────────

    @GetMapping("/verifications")
    public ResponseEntity<List<Map<String, Object>>> listVerifications(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listVerifications(status));
    }

    @GetMapping("/verifications/{id}")
    public ResponseEntity<Map<String, Object>> getVerification(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getVerification(id));
    }

    @PutMapping(value = "/verifications/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reviewVerification(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(adminService.reviewVerification(id, status, notes));
    }

    @GetMapping(value = "/verifications/{id}/files/{side}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<org.springframework.core.io.Resource> getVerificationFile(
            @PathVariable Long id,
            @PathVariable String side) {
        org.springframework.core.io.Resource resource = adminService.getVerificationFile(id, side);
        String mimeType = adminService.getVerificationFileMimeType(id, side);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // ── Credentials ─────────────────────────────────────────────────────────

    @GetMapping("/credentials")
    public ResponseEntity<List<Map<String, Object>>> listCredentials(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listCredentials(status));
    }

    @PutMapping(value = "/credentials/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reviewCredential(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(adminService.reviewCredential(id, status, notes));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Map<String, Object>> deleteCredential(@PathVariable Long id) {
        adminService.deleteCredential(id);
        return ResponseEntity.ok(Map.of("message", "Credential deleted"));
    }

    @GetMapping(value = "/credentials/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> getCredentialFile(@PathVariable Long id) {
        Resource resource = adminService.getCredentialFile(id);
        String mimeType = adminService.getCredentialMimeType(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType != null ? mimeType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    // ── Documents ────────────────────────────────────────────────────────────

    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listDocuments(status));
    }

    @GetMapping(value = "/documents/{id}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> getDocumentFile(@PathVariable Long id) {
        Resource resource = adminService.getDocumentFile(id);
        String mimeType = adminService.getDocumentMimeType(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType != null ? mimeType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    @PutMapping(value = "/documents/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reviewDocument(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        return ResponseEntity.ok(adminService.reviewDocument(id, status));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.deleteDocument(id));
    }

    // ── Institutions ─────────────────────────────────────────────────────────

    @GetMapping("/institutions")
    public ResponseEntity<List<Map<String, Object>>> listInstitutions() {
        return ResponseEntity.ok(adminService.listInstitutions());
    }

    @GetMapping("/institutions/{id}")
    public ResponseEntity<Map<String, Object>> getInstitution(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getInstitution(id));
    }

    @PatchMapping(value = "/institutions/{id}/permissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateInstitutionPermissions(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Principal principal) {
        String adminPassword = body != null ? (String) body.get("adminPassword") : null;
        adminService.verifyAdminPassword(principal.getName(), adminPassword);

        // Extract only Boolean permission flags from body
        Map<String, Boolean> perms = new java.util.HashMap<>();
        String[] permKeys = {
            "allowVerifications", "allowDocuments",
            "canViewUsers", "canManageUsers", "canDeleteUsers",
            "canViewVerifications", "canManageVerifications",
            "canViewDocuments", "canManageDocuments", "canViewActivity"
        };
        for (String key : permKeys) {
            if (body.containsKey(key) && body.get(key) instanceof Boolean) {
                perms.put(key, (Boolean) body.get(key));
            }
        }
        return ResponseEntity.ok(adminService.updateInstitutionPermissions(id, perms, principal.getName()));
    }

    @PostMapping(value = "/institutions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createInstitution(
            @RequestBody Map<String, String> body,
            Principal principal) {
        String name = body != null ? body.get("name") : null;
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        return ResponseEntity.ok(adminService.createInstitution(
                name, body.get("code"), body.get("description"),
                body.get("type"), body.get("website"), body.get("email"),
                body.get("phone"), body.get("address"), body.get("city"), body.get("country"),
                body.get("state"), body.get("pincode"), body.get("county"),
                principal.getName()));
    }

    @PutMapping(value = "/institutions/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateInstitution(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.updateInstitution(
                id, body.get("name"), body.get("code"), body.get("description"),
                body.get("type"), body.get("website"), body.get("email"),
                body.get("phone"), body.get("address"), body.get("city"), body.get("country"),
                body.get("state"), body.get("pincode"), body.get("county")));
    }

    @DeleteMapping(value = "/institutions/{id}")
    public ResponseEntity<Map<String, Object>> deleteInstitution(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        adminService.verifyAdminPassword(principal.getName(), body != null ? body.get("adminPassword") : null);
        return ResponseEntity.ok(adminService.deleteInstitution(id, principal.getName()));
    }

    @PostMapping(value = "/institutions/{id}/assign-admin", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> assignInstAdmin(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long userId = body != null && body.get("userId") != null
                ? Long.valueOf(body.get("userId").toString()) : null;
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        return ResponseEntity.ok(adminService.assignInstAdmin(id, userId));
    }

    @GetMapping("/institutions/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getInstitutionMembers(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getInstitutionMembers(id));
    }

    // ── Developer App Oversight ───────────────────────────────────────────────

    @GetMapping("/apps")
    public ResponseEntity<List<Map<String, Object>>> listApps() {
        return ResponseEntity.ok(developerAppService.listAll());
    }

    @PutMapping("/apps/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendApp(@PathVariable Long id) {
        return ResponseEntity.ok(developerAppService.setStatus(id, "SUSPENDED"));
    }

    @PutMapping("/apps/{id}/reinstate")
    public ResponseEntity<Map<String, Object>> reinstateApp(@PathVariable Long id) {
        return ResponseEntity.ok(developerAppService.setStatus(id, "ACTIVE"));
    }
}
