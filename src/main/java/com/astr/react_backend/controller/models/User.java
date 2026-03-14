package com.astr.react_backend.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone_no", unique = true, length = 20)
    private Long phoneNo;

    private LocalDate dateOfBirth;

    @Column(length = 10)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private LocalDateTime passwordUpdatedAt;

//    private Boolean isVerified;

    private LocalDateTime emailVerifiedAt;

    private LocalDateTime phoneVerifiedAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Role role; // USER, ADMIN

    @Column(name = "account_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus; // INACTIVE, ACTIVE, DISABLED

//    private LocalDateTime deactivatedAt;
//
//    @Column(length = 255)
//    private String deactivationReason;

    private LocalDateTime lastLoginAt;

    private Integer failedLoginAttempts;

//    private LocalDateTime termsAcceptedAt;
//
//    private LocalDateTime privacyPolicyAcceptedAt;

    private Boolean twoFactorEnabled;

    private Boolean marketingOptIn;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.failedLoginAttempts = 0;
        this.twoFactorEnabled = false;
        this.marketingOptIn = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.accountStatus = AccountStatus.ACTIVE;
//        this.isVerified = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public User(Long id, String username, String name, String email, Long phoneNo, LocalDate dateOfBirth, Gender gender) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.email = email;
        this.phoneNo = phoneNo;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
    }
}