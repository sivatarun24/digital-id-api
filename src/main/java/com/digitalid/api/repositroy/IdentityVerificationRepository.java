package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.IdentityVerification;
import com.digitalid.api.controller.models.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {
    Optional<IdentityVerification> findTopByUserIdOrderBySubmittedAtDesc(Long userId);
    List<IdentityVerification> findByUserIdOrderBySubmittedAtDesc(Long userId);
    boolean existsByUserIdAndStatus(Long userId, VerificationStatus status);
    List<IdentityVerification> findAllByOrderBySubmittedAtDesc();
    List<IdentityVerification> findByStatusOrderBySubmittedAtDesc(VerificationStatus status);
    List<IdentityVerification> findByUserIdInOrderBySubmittedAtDesc(List<Long> userIds);
    long countByStatus(VerificationStatus status);
}
