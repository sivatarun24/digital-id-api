package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.UserCredential;
import com.digitalid.api.controller.models.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
    List<UserCredential> findByUserId(Long userId);
    Optional<UserCredential> findByUserIdAndCredentialType(Long userId, String credentialType);
    long countByUserIdAndStatus(Long userId, VerificationStatus status);
}
