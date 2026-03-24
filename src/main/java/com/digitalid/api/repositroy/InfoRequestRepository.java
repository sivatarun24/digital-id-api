package com.digitalid.api.repositroy;

import com.digitalid.api.controller.models.InfoRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InfoRequestRepository extends JpaRepository<InfoRequest, Long> {
    List<InfoRequest> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<InfoRequest> findByUserIdAndResolvedFalseOrderByRequestedAtDesc(Long userId);
}
