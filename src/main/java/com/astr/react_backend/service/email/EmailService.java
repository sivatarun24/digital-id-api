package com.astr.react_backend.service.email;

import com.astr.react_backend.metrics.ApiMetrics;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ApiMetrics metrics;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    public EmailService(JavaMailSender mailSender, ApiMetrics metrics) {
        this.mailSender = mailSender;
        this.metrics = metrics;
    }

    // ── Generic send ─────────────────────────────────────────────

    public void send(EmailRequest request) {
        log.info("Sending {} email to {}", request.getEmailType(), request.getTo());
        long start = System.currentTimeMillis();
        try {
            if (request.isHtml()) {
                sendHtmlMail(request.getTo(), request.getSubject(), request.getBody());
            } else {
                sendPlainMail(request.getTo(), request.getSubject(), request.getBody());
            }
            metrics.recordEmailSent(request.getEmailType().name());
        } catch (Exception e) {
            metrics.recordEmailFailed();
            throw e;
        } finally {
            metrics.recordEmailDuration(System.currentTimeMillis() - start);
        }
    }

    @Async
    public void sendAsync(EmailRequest request) {
        send(request);
    }

    // ── AUTH emails ──────────────────────────────────────────────

    public void sendVerificationEmail(String to, String username, String verificationLink) {
        String subject = "Verify your email address";
        String body = buildHtml(
                "Email Verification",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>Please verify your email address by clicking the link below:</p>"
                        + "<p><a href=\"" + verificationLink + "\" "
                        + "style=\"background:#4F46E5;color:#fff;padding:10px 20px;"
                        + "text-decoration:none;border-radius:5px;\">Verify Email</a></p>"
                        + "<p>This link will expire in 24 hours.</p>"
        );
        send(authRequest(to, subject, body));
    }

    public void sendPasswordResetEmail(String to, String username, String resetLink) {
        String subject = "Reset your password";
        String body = buildHtml(
                "Password Reset",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>We received a request to reset your password. "
                        + "Click the link below to set a new password:</p>"
                        + "<p><a href=\"" + resetLink + "\" "
                        + "style=\"background:#4F46E5;color:#fff;padding:10px 20px;"
                        + "text-decoration:none;border-radius:5px;\">Reset Password</a></p>"
                        + "<p>If you didn't request this, you can safely ignore this email.</p>"
                        + "<p>This link will expire in 1 hour.</p>"
        );
        send(authRequest(to, subject, body));
    }

    public void sendWelcomeEmail(String to, String username) {
        String subject = "Welcome to " + fromName + "!";
        String body = buildHtml(
                "Welcome!",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>Thank you for creating an account. We're excited to have you on board!</p>"
                        + "<p>If you have any questions, feel free to reach out to our support team.</p>"
        );
        send(authRequest(to, subject, body));
    }

    public void sendLoginAlertEmail(String to, String username, String ipAddress, String device) {
        String subject = "New login detected on your account";
        String body = buildHtml(
                "Login Alert",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>A new login was detected on your account:</p>"
                        + "<ul>"
                        + "<li><strong>IP Address:</strong> " + ipAddress + "</li>"
                        + "<li><strong>Device:</strong> " + device + "</li>"
                        + "</ul>"
                        + "<p>If this wasn't you, please change your password immediately.</p>"
        );
        send(authRequest(to, subject, body));
    }

    // ── MARKETING emails ─────────────────────────────────────────

    public void sendPromotionalEmail(String to, String username, String promoTitle,
                                     String promoMessage, String ctaLink, String ctaText) {
        String subject = promoTitle;
        String body = buildHtml(
                promoTitle,
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>" + promoMessage + "</p>"
                        + "<p><a href=\"" + ctaLink + "\" "
                        + "style=\"background:#10B981;color:#fff;padding:10px 20px;"
                        + "text-decoration:none;border-radius:5px;\">" + ctaText + "</a></p>"
                        + unsubscribeFooter()
        );
        send(marketingRequest(to, subject, body));
    }

    public void sendNewsletterEmail(String to, String username, String newsletterHtmlContent) {
        String subject = fromName + " Newsletter";
        String body = buildHtml(
                "Newsletter",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + newsletterHtmlContent
                        + unsubscribeFooter()
        );
        send(marketingRequest(to, subject, body));
    }

    public void sendProductUpdateEmail(String to, String username, String updateTitle,
                                       String updateDetails) {
        String subject = "What's new: " + updateTitle;
        String body = buildHtml(
                updateTitle,
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>" + updateDetails + "</p>"
                        + unsubscribeFooter()
        );
        send(marketingRequest(to, subject, body));
    }

    // ── PERSONAL emails ──────────────────────────────────────────

    public void sendPersonalEmail(String to, String subject, String message) {
        String body = buildHtml(
                subject,
                "<p>" + message + "</p>"
        );
        send(personalRequest(to, subject, body));
    }

    public void sendNotificationEmail(String to, String username, String notificationMessage) {
        String subject = "You have a new notification";
        String body = buildHtml(
                "Notification",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>" + notificationMessage + "</p>"
        );
        send(personalRequest(to, subject, body));
    }

    public void sendAccountUpdateEmail(String to, String username, String whatChanged) {
        String subject = "Your account has been updated";
        String body = buildHtml(
                "Account Update",
                "<p>Hi <strong>" + username + "</strong>,</p>"
                        + "<p>The following change was made to your account:</p>"
                        + "<p><strong>" + whatChanged + "</strong></p>"
                        + "<p>If you did not make this change, please contact support immediately.</p>"
        );
        send(personalRequest(to, subject, body));
    }

    // ── Helpers ──────────────────────────────────────────────────

    private EmailRequest authRequest(String to, String subject, String body) {
        return EmailRequest.builder()
                .to(to).subject(subject).body(body)
                .emailType(EmailType.AUTH).html(true)
                .build();
    }

    private EmailRequest marketingRequest(String to, String subject, String body) {
        return EmailRequest.builder()
                .to(to).subject(subject).body(body)
                .emailType(EmailType.MARKETING).html(true)
                .build();
    }

    private EmailRequest personalRequest(String to, String subject, String body) {
        return EmailRequest.builder()
                .to(to).subject(subject).body(body)
                .emailType(EmailType.PERSONAL).html(true)
                .build();
    }

    private void sendPlainMail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromName + " <" + fromAddress + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Plain email sent to {}", to);
        } catch (MailException e) {
            log.error("Failed to send plain email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private void sendHtmlMail(String to, String subject, String htmlBody) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(mimeMessage);
            log.info("HTML email sent to {}", to);
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String buildHtml(String title, String content) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;background:#f4f4f7;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;">
                        <tr>
                          <td style="background:#4F46E5;padding:20px;text-align:center;color:#ffffff;">
                            <h1 style="margin:0;font-size:22px;">%s</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:30px;">%s</td>
                        </tr>
                        <tr>
                          <td style="padding:20px;text-align:center;font-size:12px;color:#999;">
                            &copy; %d %s. All rights reserved.
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(title, content, java.time.Year.now().getValue(), fromName);
    }

    private String unsubscribeFooter() {
        return "<hr style=\"border:none;border-top:1px solid #eee;margin:20px 0;\">"
                + "<p style=\"font-size:12px;color:#999;\">You are receiving this email because you "
                + "opted in to marketing communications. "
                + "<a href=\"#\">Unsubscribe</a></p>";
    }
}
