package com.digitalid.api.controller.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user_id")
    private Long fromUserId;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "from_email", length = 200)
    private String fromEmail;

    @Column(name = "from_role", length = 50)
    private String fromRole;

    @Column(length = 500)
    private String subject;

    @Lob
    @Column(nullable = false)
    private String body;

    @Column(nullable = false, length = 20)
    private String target; // "ADMIN" or "INST_ADMIN"

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private boolean read;

    @PrePersist
    protected void onCreate() {
        this.sentAt = LocalDateTime.now();
        this.read = false;
    }
}
