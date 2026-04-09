package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.MarketingCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, Long> {
    List<MarketingCampaign> findByStatusAndScheduledAtBefore(String status, LocalDateTime now);
}
