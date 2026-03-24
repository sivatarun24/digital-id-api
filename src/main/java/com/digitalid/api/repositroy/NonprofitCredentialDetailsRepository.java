package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.NonprofitCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NonprofitCredentialDetailsRepository extends JpaRepository<NonprofitCredentialDetails, Long> {
    Optional<NonprofitCredentialDetails> findByUserCredentialId(Long userCredentialId);
}
