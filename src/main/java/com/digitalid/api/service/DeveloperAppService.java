package com.digitalid.api.service;

import com.digitalid.api.controller.models.DeveloperApp;
import com.digitalid.api.repositroy.DeveloperAppRepository;
import com.digitalid.api.service.email.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeveloperAppService {

    private static final String KEY_PREFIX_TEMPLATE = "digid_sk_";
    private static final int PREFIX_TOTAL_LENGTH = 15; // "digid_sk_" (9) + 6 random chars

    private final DeveloperAppRepository appRepository;
    private final PasswordEncoder passwordEncoder; // reusing existing BCrypt bean
    private final EmailService emailService;       // reusing existing email service

    public DeveloperAppService(DeveloperAppRepository appRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.appRepository = appRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // ── Registration ─────────────────────────────────────────────────────────

    public Map<String, Object> register(String name, String website, String description,
                                        String callbackUrl, String allowedCredentialTypes,
                                        String ownerEmail) {
        if (name == null || name.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        if (ownerEmail == null || ownerEmail.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerEmail is required");
        if (callbackUrl == null || callbackUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callbackUrl is required");

        // Generate full API key: digid_sk_<uuid-no-dashes>
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String rawKey = KEY_PREFIX_TEMPLATE + suffix;
        String prefix = rawKey.substring(0, PREFIX_TOTAL_LENGTH);

        DeveloperApp app = DeveloperApp.builder()
                .name(name.trim())
                .website(website)
                .description(description)
                .callbackUrl(callbackUrl.trim())
                .allowedCredentialTypes(allowedCredentialTypes)
                .apiKeyHash(passwordEncoder.encode(rawKey)) // BCrypt hash
                .apiKeyPrefix(prefix)
                .ownerEmail(ownerEmail.trim().toLowerCase())
                .status("ACTIVE")
                .build();

        appRepository.save(app);

        // Confirm via email (reusing existing sendAccountUpdateEmail)
        try {
            emailService.sendAccountUpdateEmail(ownerEmail, name,
                    "Your Digital ID developer app \"" + name + "\" has been registered. " +
                    "Your API key starts with: " + prefix + "... Keep it secret!");
        } catch (Exception ignored) {}

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", app.getId());
        response.put("name", app.getName());
        response.put("apiKey", rawKey); // returned ONCE — never stored plain
        response.put("apiKeyPrefix", prefix);
        response.put("message", "Save your API key securely. It will not be shown again.");
        return response;
    }

    // ── Authentication (used by ApiKeyFilter) ────────────────────────────────

    /**
     * Given a raw API key from the request header, finds the matching active app.
     * Uses prefix for fast DB lookup, then BCrypt for full verification.
     */
    public DeveloperApp authenticate(String rawKey) {
        if (rawKey == null || rawKey.length() < PREFIX_TOTAL_LENGTH)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");

        String prefix = rawKey.substring(0, PREFIX_TOTAL_LENGTH);
        DeveloperApp app = appRepository.findByApiKeyPrefix(prefix)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));

        if (!"ACTIVE".equals(app.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Developer app is suspended");

        if (!passwordEncoder.matches(rawKey, app.getApiKeyHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");

        return app;
    }

    // ── Self-service management ─────────────────────────────────────────────

    public Map<String, Object> getMe(String rawKey) {
        DeveloperApp app = authenticate(rawKey);
        return toMap(app);
    }

    public Map<String, Object> update(String rawKey, String name, String website,
                                       String description, String callbackUrl,
                                       String allowedCredentialTypes) {
        DeveloperApp app = authenticate(rawKey);
        if (name != null && !name.isBlank()) app.setName(name.trim());
        if (website != null) app.setWebsite(website);
        if (description != null) app.setDescription(description);
        if (callbackUrl != null && !callbackUrl.isBlank()) app.setCallbackUrl(callbackUrl.trim());
        if (allowedCredentialTypes != null) app.setAllowedCredentialTypes(allowedCredentialTypes);
        appRepository.save(app);
        return toMap(app);
    }

    public Map<String, Object> regenerateKey(String rawKey) {
        DeveloperApp app = authenticate(rawKey);

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String newRawKey = KEY_PREFIX_TEMPLATE + suffix;
        String newPrefix = newRawKey.substring(0, PREFIX_TOTAL_LENGTH);

        app.setApiKeyHash(passwordEncoder.encode(newRawKey));
        app.setApiKeyPrefix(newPrefix);
        appRepository.save(app);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiKey", newRawKey);
        response.put("apiKeyPrefix", newPrefix);
        response.put("message", "API key regenerated. Save it securely — it will not be shown again.");
        return response;
    }

    public Map<String, Object> deactivate(String rawKey) {
        DeveloperApp app = authenticate(rawKey);
        app.setStatus("SUSPENDED");
        appRepository.save(app);
        return Map.of("message", "App deactivated successfully");
    }

    // ── Admin oversight ──────────────────────────────────────────────────────

    public List<Map<String, Object>> listAll() {
        return appRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> setStatus(Long appId, String status) {
        DeveloperApp app = appRepository.findById(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App not found"));
        app.setStatus(status);
        appRepository.save(app);
        return toMap(app);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public Map<String, Object> getById(Long id) {
        return appRepository.findById(id)
                .map(this::toMap)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App not found"));
    }

    private Map<String, Object> toMap(DeveloperApp app) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", app.getId());
        m.put("name", app.getName());
        m.put("website", app.getWebsite());
        m.put("description", app.getDescription());
        m.put("callbackUrl", app.getCallbackUrl());
        m.put("allowedCredentialTypes",
                app.getAllowedCredentialTypes() != null
                        ? Arrays.asList(app.getAllowedCredentialTypes().split(","))
                        : List.of());
        m.put("apiKeyPrefix", app.getApiKeyPrefix());
        m.put("status", app.getStatus());
        m.put("ownerEmail", app.getOwnerEmail());
        m.put("createdAt", app.getCreatedAt() != null ? app.getCreatedAt().toString() : null);
        return m;
    }
}
