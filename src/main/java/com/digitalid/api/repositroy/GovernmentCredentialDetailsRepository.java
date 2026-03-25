package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.GovernmentCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GovernmentCredentialDetailsRepository extends JpaRepository<GovernmentCredentialDetails, Long> {
    Optional<GovernmentCredentialDetails> findByUserCredentialId(Long userCredentialId);
}
