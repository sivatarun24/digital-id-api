package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "identity_verifications", indexes = {
        @Index(name = "idx_idverif_user_id", columnList = "user_id"),
        @Index(name = "idx_idverif_user_status", columnList = "user_id, status"),
        @Index(name = "idx_idverif_submitted", columnList = "submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "id_type", nullable = false, length = 30)
    private String idType; // drivers_license, passport, state_id, military_id, passport_card

    private String frontFilePath;
    private String backFilePath;
    private String selfieFilePath;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VerificationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewerNotes;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = VerificationStatus.PENDING;
        }
    }
}
