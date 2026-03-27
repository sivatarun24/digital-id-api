package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.DeveloperApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeveloperAppRepository extends JpaRepository<DeveloperApp, Long> {

    Optional<DeveloperApp> findByApiKeyPrefix(String apiKeyPrefix);

    List<DeveloperApp> findAllByOrderByCreatedAtDesc();
}
