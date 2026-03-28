package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.MilitaryCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MilitaryCredentialDetailsRepository extends JpaRepository<MilitaryCredentialDetails, Long> {
    Optional<MilitaryCredentialDetails> findByUserCredentialId(Long userCredentialId);
    void deleteByUserCredentialId(Long userCredentialId);
}
