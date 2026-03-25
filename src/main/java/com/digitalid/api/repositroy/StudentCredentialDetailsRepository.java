package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.StudentCredentialDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentCredentialDetailsRepository extends JpaRepository<StudentCredentialDetails, Long> {
    Optional<StudentCredentialDetails> findByUserCredentialId(Long userCredentialId);
}
