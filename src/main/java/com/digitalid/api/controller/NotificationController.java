package com.digitalid.api.controller;

import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            Authentication auth,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Long userId = getUserId(auth);
        List<Map<String, Object>> all = notificationService.getNotifications(userId, type);
        List<Map<String, Object>> page = all.stream().skip(offset).limit(limit).toList();
        long unreadCount = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of(
                "notifications", page,
                "unreadCount", unreadCount,
                "total", all.size(),
                "hasMore", (offset + limit) < all.size()
        ));
    }

    @PutMapping("/{id}/toggle-read")
    public ResponseEntity<Map<String, Object>> toggleRead(Authentication auth, @PathVariable Long id) {
        Long userId = getUserId(auth);
        return ResponseEntity.ok(notificationService.markRead(userId, id));
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication auth) {
        notificationService.markAllRead(getUserId(auth));
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> dismiss(Authentication auth, @PathVariable Long id) {
        notificationService.dismiss(getUserId(auth), id);
        return ResponseEntity.ok(Map.of("message", "Notification dismissed"));
    }

    private Long getUserId(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
