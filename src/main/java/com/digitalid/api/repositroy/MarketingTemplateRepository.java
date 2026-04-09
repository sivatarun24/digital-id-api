package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.MarketingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingTemplateRepository extends JpaRepository<MarketingTemplate, Long> {
    long countByName(String name);
}
