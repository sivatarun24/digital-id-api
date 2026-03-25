package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.HealthcareCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HealthcareCredentialDetailsRepository extends JpaRepository<HealthcareCredentialDetails, Long> {
    Optional<HealthcareCredentialDetails> findByUserCredentialId(Long userCredentialId);
}
