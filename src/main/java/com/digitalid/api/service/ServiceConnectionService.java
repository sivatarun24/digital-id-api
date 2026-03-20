package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.controller.models.UserConnectedService;
import com.digitalid.api.repositroy.ServiceConnectionRepository;
import com.digitalid.api.repositroy.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ServiceConnectionService {

    // Static service catalog
    private static final List<Map<String, Object>> SERVICE_CATALOG = List.of(
        catalog("va",        "Department of Veterans Affairs", "Government", "Access VA benefits, healthcare, and disability services."),
        catalog("ssa",       "Social Security Administration",  "Government", "View statements, apply for benefits, and manage your account."),
        catalog("login_gov", "Login.gov",                       "Government", "Secure access to participating government agencies."),
        catalog("sba",       "Small Business Administration",   "Government", "Access SBA loans, grants, and business resources."),
        catalog("usajobs",   "USAJobs",                         "Government", "Federal employment opportunities and applications."),
        catalog("tmobile",   "T-Mobile",                        "Commercial", "Military and first responder discounts on wireless plans."),
        catalog("samsung",   "Samsung",                         "Commercial", "Exclusive discounts on electronics and appliances."),
        catalog("lowes",     "Lowe's",                          "Commercial", "Military discount on eligible purchases."),
        catalog("nike",      "Nike",                            "Commercial", "Exclusive discount for military, students, and first responders."),
        catalog("spotify",   "Spotify",                         "Commercial", "Student and military discount on Premium plans.")
    );

    private static Map<String, Object> catalog(String id, String name, String category, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("desc", desc);
        return m;
    }

    private final ServiceConnectionRepository serviceRepo;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ServiceConnectionService(ServiceConnectionRepository serviceRepo,
                                     UserRepository userRepository,
                                     NotificationService notificationService,
                                     AuditLogService auditLogService) {
        this.serviceRepo = serviceRepo;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    public List<Map<String, Object>> getServices(String username) {
        User user = getUser(username);
        Map<String, UserConnectedService> connected = serviceRepo.findByUserId(user.getId())
                .stream().collect(Collectors.toMap(UserConnectedService::getServiceSlug, s -> s));

        return SERVICE_CATALOG.stream().map(svc -> {
            Map<String, Object> entry = new LinkedHashMap<>(svc);
            UserConnectedService conn = connected.get(svc.get("id"));
            entry.put("connected", conn != null);
            if (conn != null) {
                entry.put("connectedAt", conn.getConnectedAt().toLocalDate().toString());
                entry.put("lastUsed", conn.getLastUsedAt() != null
                        ? conn.getLastUsedAt().toLocalDate().toString() : null);
            }
            return entry;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> connect(String username, String serviceSlug) {
        User user = getUser(username);
        if (serviceRepo.existsByUserIdAndServiceSlug(user.getId(), serviceSlug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service already connected");
        }
        String serviceName = getServiceName(serviceSlug);
        UserConnectedService conn = UserConnectedService.builder()
                .userId(user.getId())
                .serviceSlug(serviceSlug)
                .build();
        serviceRepo.save(conn);

        notificationService.create(user.getId(), "service",
                "Service connected",
                "Your Digital ID has been connected to " + serviceName + ".");
        auditLogService.log(username, AuditAction.SERVICE_CONNECTED, serviceSlug);

        return Map.of("message", "Connected to " + serviceName, "connected", true);
    }

    public void disconnect(String username, String serviceSlug) {
        User user = getUser(username);
        UserConnectedService conn = serviceRepo.findByUserIdAndServiceSlug(user.getId(), serviceSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not connected"));
        serviceRepo.delete(conn);
        auditLogService.log(username, AuditAction.SERVICE_DISCONNECTED, serviceSlug);
    }

    public long countConnected(Long userId) {
        return serviceRepo.countByUserId(userId);
    }

    private String getServiceName(String slug) {
        return SERVICE_CATALOG.stream()
                .filter(s -> slug.equals(s.get("id")))
                .map(s -> (String) s.get("name"))
                .findFirst().orElse(slug);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
