package com.digitalid.api.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void log(String username, AuditAction action, String details,
                    String ipAddress, String userAgent) {
        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(entry);

        auditLogger.info("AUDIT | user={} | action={} | ip={} | detail={}",
                username, action, ipAddress, details);
    }

    @Async
    public void log(String username, AuditAction action, String details) {
        log(username, action, details, null, null);
    }
}
