package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByTargetOrderBySentAtDesc(String target);
    List<SupportMessage> findByFromUserIdOrderBySentAtDesc(Long fromUserId);
}
