package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_connected_services", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "service_slug"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserConnectedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "service_slug", nullable = false, length = 50)
    private String serviceSlug;

    @Column(nullable = false, updatable = false)
    private LocalDateTime connectedAt;

    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        this.connectedAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
    }
}
