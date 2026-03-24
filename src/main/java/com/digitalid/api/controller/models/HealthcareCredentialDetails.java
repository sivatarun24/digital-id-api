package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "healthcare_credential_details", indexes = {
        @Index(name = "idx_healthcare_credential_id", columnList = "user_credential_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthcareCredentialDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_credential_id", nullable = false, unique = true)
    private Long userCredentialId;

    private String licenseType;
    private String licenseNumber;
    private String issuingState;
    private String employer;
}
