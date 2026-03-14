package com.astr.react_backend.service.email;

import com.astr.react_backend.metrics.ApiMetrics;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private ApiMetrics metrics;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, metrics);
        ReflectionTestUtils.setField(emailService, "fromAddress", "test@test.com");
        ReflectionTestUtils.setField(emailService, "fromName", "TestApp");
    }

    @Test
    void send_plainTextEmail_shouldUseSimpleMailMessage() {
        EmailRequest request = EmailRequest.builder()
                .to("user@example.com")
                .subject("Test Subject")
                .body("Test body")
                .emailType(EmailType.PERSONAL)
                .html(false)
                .build();

        emailService.send(request);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertArrayEquals(new String[]{"user@example.com"}, sent.getTo());
        assertEquals("Test Subject", sent.getSubject());
        assertEquals("Test body", sent.getText());
    }

    @Test
    void send_htmlEmail_shouldUseMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailRequest request = EmailRequest.builder()
                .to("user@example.com")
                .subject("HTML Test")
                .body("<h1>Hello</h1>")
                .emailType(EmailType.AUTH)
                .html(true)
                .build();

        emailService.send(request);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendWelcomeEmail_shouldSendHtmlEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendWelcomeEmail("user@example.com", "testuser");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetEmail_shouldSendHtmlEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPasswordResetEmail("user@example.com", "testuser", "http://example.com/reset");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendAccountUpdateEmail_shouldSendHtmlEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendAccountUpdateEmail("user@example.com", "testuser", "Password changed");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPromotionalEmail_shouldSendHtmlEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendPromotionalEmail("user@example.com", "testuser",
                "50% Off", "Limited offer", "http://example.com/deal", "Shop Now");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendNotificationEmail_shouldSendHtmlEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendNotificationEmail("user@example.com", "testuser", "You have a new message");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void send_whenMailFails_shouldThrowRuntimeException() {
        EmailRequest request = EmailRequest.builder()
                .to("user@example.com")
                .subject("Fail Test")
                .body("body")
                .emailType(EmailType.AUTH)
                .html(false)
                .build();

        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(RuntimeException.class, () -> emailService.send(request));
    }
}
