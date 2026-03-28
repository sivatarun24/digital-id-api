package com.digitalid.api.service;

import com.digitalid.api.repositroy.UserCredentialRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class VerificationAutomationService {

    // Trusted domains for instant verification
    private static final Map<String, Set<String>> TRUSTED_DOMAINS = Map.of(
            "student", Set.of("edu", "ac.uk", "edu.au", "edu.in"),
            "teacher", Set.of("edu", "k12.wa.us", "schools.gov"),
            "government", Set.of("gov", "fed.us"),
            "military", Set.of("mil"),
            "nonprofit", Set.of("org", "charity.org")
    );

    private final com.digitalid.api.service.email.EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public VerificationAutomationService(com.digitalid.api.service.email.EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Checks if an email address is eligible for instant domain verification
     * for a given credential type.
     */
    public boolean isEligibleForInstantVerify(String email, String credentialType) {
        if (email == null || !email.contains("@")) return false;
        
        String domain = email.substring(email.lastIndexOf(".") + 1).toLowerCase();
        Set<String> allowedExtensions = TRUSTED_DOMAINS.get(credentialType);
        
        return allowedExtensions != null && allowedExtensions.contains(domain);
    }

    public String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }

    public void sendVerificationEmail(String to, String username, String credentialType, String token) {
        String typeDisplay = credentialType.replace("_", " ");
        String verifyLink = frontendUrl + "/verify-credential?token=" + token;
        
        String subject = "Verify your " + typeDisplay + " credential";
        String body = """
            <p>Hi <strong>%s</strong>,</p>
            <p>You requested to verify your <strong>%s</strong> affiliation.</p>
            <p>Please click the link below to instantly verify your account:</p>
            <p style="margin: 24px 0;">
                <a href="%s" style="background:#059669;color:#fff;padding:12px 24px;text-decoration:none;border-radius:6px;font-weight:600;">
                    Verify Credential
                </a>
            </p>
            <p style="color:#666;font-size:14px;">If you didn't request this, you can ignore this email.</p>
            """.formatted(username, typeDisplay, verifyLink);

        com.digitalid.api.service.email.EmailRequest request = com.digitalid.api.service.email.EmailRequest.builder()
                .to(to)
                .subject(subject)
                .body(body)
                .emailType(com.digitalid.api.service.email.EmailType.CREDENTIAL)
                .html(true)
                .build();
        
        emailService.sendAsync(request);
    }

    public boolean isProfessionalEmail(String email) {
        if (email == null) return false;
        String lower = email.toLowerCase();
        return !lower.contains("gmail.com") && 
               !lower.contains("yahoo.com") && 
               !lower.contains("outlook.com") && 
               !lower.contains("hotmail.com") &&
               !lower.contains("icloud.com");
    }
}
