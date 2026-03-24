package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "senior_credential_details", indexes = {
        @Index(name = "idx_senior_credential_id", columnList = "user_credential_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeniorCredentialDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_credential_id", nullable = false, unique = true)
    private Long userCredentialId;

    private LocalDate dateOfBirth;
}
