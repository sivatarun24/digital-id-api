package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.FirstResponderCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FirstResponderCredentialDetailsRepository extends JpaRepository<FirstResponderCredentialDetails, Long> {
    Optional<FirstResponderCredentialDetails> findByUserCredentialId(Long userCredentialId);
}
