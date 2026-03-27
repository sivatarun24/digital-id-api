package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "developer_apps",
        indexes = { @Index(name = "idx_app_prefix", columnList = "api_key_prefix") }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeveloperApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String website;

    @Column(length = 500)
    private String description;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    /** Comma-separated list: student,military,healthcare,... */
    @Column(name = "allowed_credential_types", columnDefinition = "TEXT")
    private String allowedCredentialTypes;

    /** BCrypt hash of the full API key — never stored plain */
    @Column(name = "api_key_hash", nullable = false, length = 255)
    private String apiKeyHash;

    /** First 15 chars of the raw key, used for fast DB lookup before BCrypt check */
    @Column(name = "api_key_prefix", nullable = false, length = 15)
    private String apiKeyPrefix;

    /** ACTIVE | SUSPENDED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "owner_email", nullable = false, length = 100)
    private String ownerEmail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "ACTIVE";
    }
}
