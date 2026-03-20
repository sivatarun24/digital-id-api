package com.digitalid.api.service;

import com.digitalid.api.controller.models.Notification;
import com.digitalid.api.repositroy.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Map<String, Object>> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public Map<String, Object> markRead(Long userId, Long notifId) {
        Notification n = notificationRepository.findByIdAndUserId(notifId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(!n.isRead());
        return toMap(notificationRepository.save(n));
    }

    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    public void dismiss(Long userId, Long notifId) {
        Notification n = notificationRepository.findByIdAndUserId(notifId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notificationRepository.delete(n);
    }

    public void create(Long userId, String type, String title, String message) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .build();
        notificationRepository.save(n);
    }

    private Map<String, Object> toMap(Notification n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.getId());
        map.put("type", n.getType());
        map.put("title", n.getTitle());
        map.put("message", n.getMessage());
        map.put("read", n.isRead());
        map.put("time", n.getCreatedAt().toString());
        return map;
    }
}
