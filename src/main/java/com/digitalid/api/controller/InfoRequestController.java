package com.digitalid.api.controller;

import com.digitalid.api.service.InfoRequestService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class InfoRequestController {

    private final InfoRequestService infoRequestService;

    public InfoRequestController(InfoRequestService infoRequestService) {
        this.infoRequestService = infoRequestService;
    }

    // ── Admin endpoints (/api/admin/info-requests) ────────────────────────────

    @PostMapping(value = "/api/admin/info-requests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestBody Map<String, Object> body,
            Principal principal) {
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        String note = (String) body.get("note");
        String source = body.getOrDefault("source", "user_detail").toString();
        if (userId == null || note == null || note.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and note are required"));
        }
        return ResponseEntity.status(201).body(
            infoRequestService.createRequest(userId, note, source, principal.getName()));
    }

    @GetMapping(value = "/api/admin/info-requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> listRequestsForUser(
            @RequestParam Long userId) {
        return ResponseEntity.ok(infoRequestService.getRequestsForUser(userId));
    }

    @DeleteMapping(value = "/api/admin/info-requests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteRequest(@PathVariable Long id) {
        infoRequestService.deleteRequest(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @GetMapping(value = "/api/admin/info-requests/{id}/files/{fileIndex}")
    public ResponseEntity<Resource> getResponseFileAdmin(
            @PathVariable Long id,
            @PathVariable int fileIndex,
            Principal principal) {
        Resource resource = infoRequestService.getResponseFile(id, fileIndex, principal.getName(), true);
        String mimeType = infoRequestService.getResponseFileMimeType(id, fileIndex);
        String fileName = infoRequestService.getResponseFileName(id, fileIndex);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(mimeType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
            .body(resource);
    }

    // ── User endpoints (/api/info-requests) ──────────────────────────────────

    @GetMapping(value = "/api/info-requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getMyRequests(Authentication auth) {
        return ResponseEntity.ok(infoRequestService.getPendingRequestsForUser(auth.getName()));
    }

    @PostMapping(value = "/api/info-requests/{id}/respond", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> respond(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "files", required = false) MultipartFile[] files) throws IOException {
        return ResponseEntity.ok(
            infoRequestService.respondToRequest(auth.getName(), id, message, files));
    }

    @GetMapping(value = "/api/info-requests/{id}/files/{fileIndex}")
    public ResponseEntity<Resource> getResponseFileUser(
            @PathVariable Long id,
            @PathVariable int fileIndex,
            Authentication auth) {
        Resource resource = infoRequestService.getResponseFile(id, fileIndex, auth.getName(), false);
        String mimeType = infoRequestService.getResponseFileMimeType(id, fileIndex);
        String fileName = infoRequestService.getResponseFileName(id, fileIndex);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(mimeType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
            .body(resource);
    }
}
