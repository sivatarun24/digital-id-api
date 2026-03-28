package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.UserCredential;
import com.digitalid.api.controller.models.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
    List<UserCredential> findAllByOrderByStartedAtDesc();
    List<UserCredential> findByUserId(Long userId);
    List<UserCredential> findByUserIdOrderByStartedAtDesc(Long userId);
    List<UserCredential> findByUserIdInOrderByStartedAtDesc(List<Long> userIds);
    Optional<UserCredential> findByUserIdAndCredentialType(Long userId, String credentialType);
    Optional<UserCredential> findByVerificationToken(String token);
    long countByUserIdAndStatus(Long userId, VerificationStatus status);
}
