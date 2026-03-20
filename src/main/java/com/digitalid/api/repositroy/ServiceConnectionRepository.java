package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.UserConnectedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceConnectionRepository extends JpaRepository<UserConnectedService, Long> {
    List<UserConnectedService> findByUserId(Long userId);
    Optional<UserConnectedService> findByUserIdAndServiceSlug(Long userId, String serviceSlug);
    boolean existsByUserIdAndServiceSlug(Long userId, String serviceSlug);
    long countByUserId(Long userId);
}
