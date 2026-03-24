package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "military_credential_details", indexes = {
        @Index(name = "idx_military_credential_id", columnList = "user_credential_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilitaryCredentialDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_credential_id", nullable = false, unique = true)
    private Long userCredentialId;

    private String branch;
    private String rank;
    private LocalDate serviceStartDate;
    private Boolean currentlyServing;
    private LocalDate serviceEndDate;
    private String dischargeType;
}
