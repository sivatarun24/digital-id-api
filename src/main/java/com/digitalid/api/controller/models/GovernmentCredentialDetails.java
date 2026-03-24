package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "government_credential_details", indexes = {
        @Index(name = "idx_government_credential_id", columnList = "user_credential_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GovernmentCredentialDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_credential_id", nullable = false, unique = true)
    private Long userCredentialId;

    private String agencyName;
    private String position;
    private String level;
    private String employeeId;
}
