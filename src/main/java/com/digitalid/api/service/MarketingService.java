package com.digitalid.api.service;

import com.digitalid.api.controller.models.MarketingCampaign;
import com.digitalid.api.controller.models.MarketingTemplate;
import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.MarketingCampaignRepository;
import com.digitalid.api.repositroy.MarketingTemplateRepository;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.email.EmailService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketingService {

    private static final Logger log = LoggerFactory.getLogger(MarketingService.class);

    private final MarketingTemplateRepository templateRepo;
    private final MarketingCampaignRepository campaignRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    public MarketingService(
            MarketingTemplateRepository templateRepo,
            MarketingCampaignRepository campaignRepo,
            UserRepository userRepo,
            EmailService emailService) {
        this.templateRepo = templateRepo;
        this.campaignRepo = campaignRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
    }

    // ── Seed 10 default templates ──────────────────────────────────────────

    @PostConstruct
    @Transactional
    public void seedTemplates() {
        if (templateRepo.count() > 0) return;

        List<Object[]> seeds = List.of(
            new Object[]{"Welcome to Digital ID", "Welcome to Your Digital ID Account!", "welcome", "<h2>Welcome to Digital ID!</h2><p>Your identity is now securely managed. Explore your dashboard to get started.</p>"},
            new Object[]{"Identity Verification Reminder", "Complete Your Identity Verification", "verification", "<h2>Verify Your Identity</h2><p>Unlock the full power of Digital ID by completing your identity verification today.</p>"},
            new Object[]{"New Service Available", "A New Service Just Connected to Digital ID", "services", "<h2>New Partner Service</h2><p>A new government or commercial service is now available through Digital ID. Check your services tab.</p>"},
            new Object[]{"Security Tips", "Keep Your Account Secure", "security", "<h2>Security Best Practices</h2><p>Enable two-factor authentication and review your connected services regularly to stay protected.</p>"},
            new Object[]{"Document Expiry Alert", "Your Documents May Be Expiring Soon", "documents", "<h2>Check Your Documents</h2><p>Some of your uploaded identity documents may be approaching their expiry date. Please review and update them.</p>"},
            new Object[]{"Monthly Platform Update", "What's New in Digital ID This Month", "newsletter", "<h2>Platform Updates</h2><p>We've added new features, improved performance, and expanded our partner services. See what's new!</p>"},
            new Object[]{"Special Offer from Partner", "Exclusive Offer for Digital ID Members", "promotion", "<h2>Member-Exclusive Offer</h2><p>As a verified Digital ID member, you qualify for exclusive discounts from our partner network.</p>"},
            new Object[]{"Credential Expiry Reminder", "Your Credential Is About to Expire", "credentials", "<h2>Renew Your Credential</h2><p>One or more of your credentials is approaching its expiration. Renew now to maintain access.</p>"},
            new Object[]{"Re-engagement Campaign", "We Miss You! Come Back to Digital ID", "engagement", "<h2>It's Been a While</h2><p>Your Digital ID account is still active and waiting for you. Log in today to check for updates.</p>"},
            new Object[]{"Privacy Policy Update", "Important: Updated Privacy Policy", "policy", "<h2>Privacy Policy Update</h2><p>We've updated our privacy policy to better protect your data. Please review the changes at your convenience.</p>"}
        );

        for (Object[] seed : seeds) {
            MarketingTemplate t = MarketingTemplate.builder()
                    .name((String) seed[0])
                    .subject((String) seed[1])
                    .category((String) seed[2])
                    .bodyHtml((String) seed[3])
                    .build();
            templateRepo.save(t);
        }
    }

    // ── Templates ──────────────────────────────────────────────────────────

    public List<Map<String, Object>> listTemplates() {
        return templateRepo.findAll().stream()
                .map(this::templateToMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getTemplate(Long id) {
        return templateRepo.findById(id)
                .map(this::templateToMap)
                .orElseThrow(() -> new RuntimeException("Template not found"));
    }

    @Transactional
    public Map<String, Object> createTemplate(Map<String, String> body) {
        MarketingTemplate t = MarketingTemplate.builder()
                .name(body.get("name"))
                .subject(body.get("subject"))
                .bodyHtml(body.get("bodyHtml"))
                .category(body.getOrDefault("category", "general"))
                .build();
        return templateToMap(templateRepo.save(t));
    }

    @Transactional
    public Map<String, Object> updateTemplate(Long id, Map<String, String> body) {
        MarketingTemplate t = templateRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        if (body.containsKey("name")) t.setName(body.get("name"));
        if (body.containsKey("subject")) t.setSubject(body.get("subject"));
        if (body.containsKey("bodyHtml")) t.setBodyHtml(body.get("bodyHtml"));
        if (body.containsKey("category")) t.setCategory(body.get("category"));
        return templateToMap(templateRepo.save(t));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepo.deleteById(id);
    }

    // ── Campaigns ──────────────────────────────────────────────────────────

    public List<Map<String, Object>> listCampaigns() {
        return campaignRepo.findAll().stream()
                .map(this::campaignToMap)
                .sorted((a, b) -> {
                    String ca = (String) a.getOrDefault("createdAt", "");
                    String cb = (String) b.getOrDefault("createdAt", "");
                    return cb.compareTo(ca);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createCampaign(Map<String, Object> body) {
        Long templateId = body.get("templateId") != null
                ? Long.parseLong(body.get("templateId").toString()) : null;
        MarketingTemplate template = templateId != null
                ? templateRepo.findById(templateId).orElse(null) : null;

        String scheduledAtStr = body.get("scheduledAt") != null ? body.get("scheduledAt").toString() : null;
        LocalDateTime scheduledAt = scheduledAtStr != null && !scheduledAtStr.isBlank()
                ? LocalDateTime.parse(scheduledAtStr) : null;

        MarketingCampaign c = MarketingCampaign.builder()
                .name(body.get("name") != null ? body.get("name").toString() : "Untitled Campaign")
                .template(template)
                .targetAudience(body.get("targetAudience") != null ? body.get("targetAudience").toString() : "OPTED_IN")
                .status(scheduledAt != null ? "SCHEDULED" : "DRAFT")
                .scheduledAt(scheduledAt)
                .sentCount(0)
                .build();
        return campaignToMap(campaignRepo.save(c));
    }

    @Transactional
    public Map<String, Object> sendCampaign(Long id) {
        MarketingCampaign c = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        if ("SENT".equals(c.getStatus()) || "CANCELLED".equals(c.getStatus())) {
            throw new RuntimeException("Campaign cannot be sent in status: " + c.getStatus());
        }
        return executeSend(c);
    }

    @Transactional
    public Map<String, Object> cancelCampaign(Long id) {
        MarketingCampaign c = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        c.setStatus("CANCELLED");
        return campaignToMap(campaignRepo.save(c));
    }

    @Transactional
    public Map<String, Object> updateCampaign(Long id, Map<String, Object> body) {
        MarketingCampaign c = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        if ("SENT".equals(c.getStatus())) throw new RuntimeException("Cannot edit a sent campaign");

        if (body.containsKey("name")) c.setName(body.get("name").toString());
        if (body.containsKey("targetAudience")) c.setTargetAudience(body.get("targetAudience").toString());
        if (body.containsKey("templateId")) {
            Long tId = Long.parseLong(body.get("templateId").toString());
            c.setTemplate(templateRepo.findById(tId).orElse(null));
        }
        if (body.containsKey("scheduledAt")) {
            String s = body.get("scheduledAt").toString();
            c.setScheduledAt(s.isBlank() ? null : LocalDateTime.parse(s));
            c.setStatus(c.getScheduledAt() != null ? "SCHEDULED" : "DRAFT");
        }
        return campaignToMap(campaignRepo.save(c));
    }

    // ── Scheduled send ─────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processScheduledCampaigns() {
        List<MarketingCampaign> due = campaignRepo
                .findByStatusAndScheduledAtBefore("SCHEDULED", LocalDateTime.now());
        for (MarketingCampaign c : due) {
            try {
                executeSend(c);
            } catch (Exception e) {
                log.error("[Marketing] Scheduled campaign id={} failed: {}", c.getId(), e.getMessage());
            }
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private Map<String, Object> executeSend(MarketingCampaign c) {
        List<User> users = "ALL".equals(c.getTargetAudience())
                ? userRepo.findAll()
                : userRepo.findByMarketingOptIn(true);

        MarketingTemplate t = c.getTemplate();
        if (t != null) {
            for (User u : users) {
                try {
                    emailService.sendHtmlEmail(u.getEmail(), t.getSubject(), t.getBodyHtml());
                } catch (Exception e) {
                    log.warn("[Marketing] Email failed for user {}: {}", u.getEmail(), e.getMessage());
                }
            }
        }
        c.setStatus("SENT");
        c.setSentAt(LocalDateTime.now());
        c.setSentCount(users.size());
        return campaignToMap(campaignRepo.save(c));
    }

    private Map<String, Object> templateToMap(MarketingTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("subject", t.getSubject());
        m.put("bodyHtml", t.getBodyHtml());
        m.put("category", t.getCategory());
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        m.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> campaignToMap(MarketingCampaign c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("templateId", c.getTemplate() != null ? c.getTemplate().getId() : null);
        m.put("templateName", c.getTemplate() != null ? c.getTemplate().getName() : null);
        m.put("status", c.getStatus());
        m.put("targetAudience", c.getTargetAudience());
        m.put("scheduledAt", c.getScheduledAt() != null ? c.getScheduledAt().toString() : null);
        m.put("sentAt", c.getSentAt() != null ? c.getSentAt().toString() : null);
        m.put("sentCount", c.getSentCount());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return m;
    }
}
