package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "student_credential_details", indexes = {
        @Index(name = "idx_student_credential_id", columnList = "user_credential_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCredentialDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_credential_id", nullable = false, unique = true)
    private Long userCredentialId;

    private String schoolName;
    private String enrollmentStatus;
    private String major;
    private String studentId;
    private String graduationDate;
}
