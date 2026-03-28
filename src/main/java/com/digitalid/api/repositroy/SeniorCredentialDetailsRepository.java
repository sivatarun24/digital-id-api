package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.SeniorCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeniorCredentialDetailsRepository extends JpaRepository<SeniorCredentialDetails, Long> {
    Optional<SeniorCredentialDetails> findByUserCredentialId(Long userCredentialId);
    void deleteByUserCredentialId(Long userCredentialId);
}
