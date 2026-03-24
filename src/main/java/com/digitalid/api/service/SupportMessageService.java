package com.digitalid.api.service;

import com.digitalid.api.controller.models.SupportMessage;
import com.digitalid.api.repositroy.SupportMessageRepository;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.controller.models.User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SupportMessageService {

    private final SupportMessageRepository repo;
    private final UserRepository userRepository;

    public SupportMessageService(SupportMessageRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    public Map<String, Object> send(String senderUsername, String subject, String body, String target) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        SupportMessage msg = SupportMessage.builder()
                .fromUserId(sender.getId())
                .fromName(sender.getName() != null ? sender.getName() : sender.getUsername())
                .fromEmail(sender.getEmail())
                .fromRole(sender.getRole() != null ? sender.getRole().name() : "USER")
                .subject(subject != null ? subject : "")
                .body(body)
                .target(target)
                .build();
        return toMap(repo.save(msg));
    }

    public List<Map<String, Object>> getMessages(String target) {
        return repo.findByTargetOrderBySentAtDesc(target)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getMessagesForSender(String username) {
        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return repo.findByFromUserIdOrderBySentAtDesc(sender.getId())
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public void markRead(Long id) {
        repo.findById(id).ifPresent(msg -> {
            msg.setRead(true);
            repo.save(msg);
        });
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    private Map<String, Object> toMap(SupportMessage msg) {
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("id", msg.getFromUserId());
        from.put("name", msg.getFromName());
        from.put("email", msg.getFromEmail());
        from.put("role", msg.getFromRole());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", msg.getId());
        result.put("from", from);
        result.put("subject", msg.getSubject());
        result.put("body", msg.getBody());
        result.put("target", msg.getTarget());
        result.put("sentAt", msg.getSentAt() != null ? msg.getSentAt().toString() : null);
        result.put("read", msg.isRead());
        return result;
    }
}
