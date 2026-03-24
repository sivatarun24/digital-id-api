package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "institutions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(unique = true, length = 20)
    private String code;

    @Column(length = 255)
    private String description;

    /** Institution category: UNIVERSITY, GOVERNMENT, HEALTHCARE, BANK, CORPORATE, NGO, OTHER */
    @Column(length = 50)
    private String type;

    @Column(length = 100)
    private String website;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(length = 100)
    private String county;

    /** Coarse access flags (legacy) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean allowVerifications = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowDocuments = true;

    /** Fine-grained institutional-admin permissions */
    @Column(nullable = false)
    @Builder.Default
    private Boolean canViewUsers = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canManageUsers = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canDeleteUsers = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canViewVerifications = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canManageVerifications = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canViewDocuments = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canManageDocuments = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canViewActivity = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
