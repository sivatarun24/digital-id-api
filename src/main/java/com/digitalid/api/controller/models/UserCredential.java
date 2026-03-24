package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_credentials",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "credential_type"})
        },
        indexes = {
                @Index(name = "idx_cred_user_id", columnList = "user_id"),
                @Index(name = "idx_cred_user_status", columnList = "user_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "credential_type", nullable = false, length = 30)
    private String credentialType; // military, student, first_responder, teacher, healthcare, government, senior

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VerificationStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime verifiedAt;

    @Column(length = 1000)
    private String reviewerNotes;

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = VerificationStatus.PENDING;
        }
    }
}
