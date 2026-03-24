package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "nonprofit_credential_details", indexes = {
        @Index(name = "idx_nonprofit_credential_id", columnList = "user_credential_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NonprofitCredentialDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_credential_id", nullable = false, unique = true)
    private Long userCredentialId;

    private String orgName;
    private String ein;
    private String position;
    private String orgType;
    private LocalDate employmentStartDate;
}
