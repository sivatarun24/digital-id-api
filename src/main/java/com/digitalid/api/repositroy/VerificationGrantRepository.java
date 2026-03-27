package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.VerificationGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationGrantRepository extends JpaRepository<VerificationGrant, Long> {

    Optional<VerificationGrant> findByToken(String token);

    List<VerificationGrant> findByUserIdOrderByCreatedAtDesc(Long userId);
}
