package com.digitalid.api.controller;

import com.digitalid.api.service.SupportMessageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class MessageController {

    private final SupportMessageService service;

    public MessageController(SupportMessageService service) {
        this.service = service;
    }

    // ── Any authenticated user sends a message ───────────────────────────────

    @PostMapping(value = "/api/messages", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestBody Map<String, Object> body,
            Principal principal) {
        String subject = body.getOrDefault("subject", "").toString();
        String msgBody = (String) body.get("body");
        String target  = (String) body.get("target");
        if (msgBody == null || msgBody.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "body is required"));
        }
        if (!"ADMIN".equals(target) && !"INST_ADMIN".equals(target)) {
            return ResponseEntity.badRequest().body(Map.of("error", "target must be ADMIN or INST_ADMIN"));
        }
        return ResponseEntity.status(201).body(service.send(principal.getName(), subject, msgBody, target));
    }

    @GetMapping(value = "/api/messages/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getMyMessages(Principal principal) {
        return ResponseEntity.ok(service.getMessagesForSender(principal.getName()));
    }

    // ── Admin inbox (/api/admin/messages — requires ADMIN) ───────────────────

    @GetMapping(value = "/api/admin/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getAdminMessages() {
        return ResponseEntity.ok(service.getMessages("ADMIN"));
    }

    @PutMapping(value = "/api/admin/messages/{id}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> markAdminRead(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @DeleteMapping(value = "/api/admin/messages/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteAdminMessage(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // ── Inst-admin inbox (/api/inst-admin/messages — requires ADMIN or INST_ADMIN) ──

    @GetMapping(value = "/api/inst-admin/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getInstMessages() {
        return ResponseEntity.ok(service.getMessages("INST_ADMIN"));
    }

    @PutMapping(value = "/api/inst-admin/messages/{id}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> markInstRead(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @DeleteMapping(value = "/api/inst-admin/messages/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteInstMessage(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}
