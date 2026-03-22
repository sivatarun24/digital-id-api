package com.digitalid.api.service;

import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.metrics.ApiMetrics;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApiMetrics metrics;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");

        lenient().when(metrics.timeLogin(any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        lenient().when(metrics.timeRegister(any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .phoneNo(1234567890L)
                .passwordHash("encoded_password")
                .role(Role.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();
    }

    // ── Login tests ──────────────────────────────────────────

    @Test
    void userLogin_withValidCredentials_shouldReturnTokens() {
        LoginRequest request = new LoginRequest("testuser", null, null, "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-token");

        Map<String, Object> result = authService.userLogin(request, "127.0.0.1", "TestAgent");

        assertEquals("access-token", result.get("accessToken"));
        assertEquals("refresh-token", result.get("refreshToken"));
        assertEquals("Bearer", result.get("tokenType"));
        verify(userRepository).save(testUser);
        verify(auditLogService).log(eq("testuser"), any(), eq("Login successful"), anyString(), anyString());
    }

    @Test
    void userLogin_withInvalidPassword_shouldThrowAndIncrementAttempts() {
        LoginRequest request = new LoginRequest("testuser", null, null, "wrong");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "encoded_password")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.userLogin(request, "127.0.0.1", "TestAgent"));

        assertEquals(1, testUser.getFailedLoginAttempts());
        verify(userRepository).save(testUser);
    }

    @Test
    void userLogin_withInactiveAccount_shouldThrowForbidden() {
        testUser.setAccountStatus(AccountStatus.INACTIVE);
        LoginRequest request = new LoginRequest("testuser", null, null, "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.userLogin(request, "127.0.0.1", "TestAgent"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void userLogin_withDisabledAccount_shouldThrowForbidden() {
        testUser.setAccountStatus(AccountStatus.DISABLED);
        LoginRequest request = new LoginRequest("testuser", null, null, "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.userLogin(request, "127.0.0.1", "TestAgent"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void userLogin_withEmail_shouldResolveUser() {
        LoginRequest request = new LoginRequest(null, "test@example.com", null, "password123");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("token");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh");

        Map<String, Object> result = authService.userLogin(request, "127.0.0.1", "TestAgent");

        assertNotNull(result.get("accessToken"));
    }

    @Test
    void userLogin_withNoIdentifier_shouldThrow() {
        LoginRequest request = new LoginRequest(null, null, null, "password123");

        assertThrows(ResponseStatusException.class,
                () -> authService.userLogin(request, "127.0.0.1", "TestAgent"));
    }

    // ── Register tests ───────────────────────────────────────

    @Test
    void registerUser_withValidData_shouldSucceed() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "New User", "new@example.com",
                9876543210L, LocalDate.of(1995, 1, 1), Gender.MALE, Role.USER, "password123", true);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNo(9876543210L)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(jwtService.generateEmailVerificationToken(any())).thenReturn("verify-token");

        Map<String, Object> result = authService.registerUser(request, "127.0.0.1", "TestAgent");

        assertTrue(result.get("message").toString().contains("check your email"));
        verify(userRepository).save(any(User.class));
        verify(emailService).sendVerificationEmail(eq("new@example.com"), eq("newuser"), contains("verify-token"));
    }

    @Test
    void registerUser_withDuplicateUsername_shouldThrow() {
        RegisterRequest request = new RegisterRequest(
                "testuser", "Test", "new@example.com",
                9876543210L, null, null, Role.USER, "password123", true);

        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(request, "127.0.0.1", "TestAgent"));
    }

    @Test
    void registerUser_withDuplicateEmail_shouldThrow() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "Test", "test@example.com",
                9876543210L, null, null, Role.USER, "password123", true);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(request, "127.0.0.1", "TestAgent"));
    }

    @Test
    void registerUser_withDuplicatePhone_shouldThrow() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "Test", "new@example.com",
                1234567890L, null, null, Role.USER, "password123", true);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNo(1234567890L)).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(request, "127.0.0.1", "TestAgent"));
    }

    @Test
    void registerUser_withShortPassword_shouldThrow() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "New", "new@example.com",
                9876543210L, null, null, Role.USER, "short", true);

        assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(request, "127.0.0.1", "TestAgent"));
    }

    @Test
    void registerUser_withNullPassword_shouldThrow() {
        RegisterRequest request = new RegisterRequest(
                "newuser", "New", "new@example.com",
                9876543210L, null, null, Role.USER, null, true);

        assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(request, "127.0.0.1", "TestAgent"));
    }

    // ── Forgot / Reset password tests ────────────────────────

    @Test
    void forgotPassword_withValidEmail_shouldSendResetEmail() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generatePasswordResetToken(testUser)).thenReturn("reset-token");

        Map<String, Object> result = authService.forgotPassword("test@example.com", "127.0.0.1", "TestAgent");

        assertTrue(result.get("message").toString().contains("sent"));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), eq("testuser"),
                contains("reset-token"));
    }

    @Test
    void forgotPassword_withUnknownEmail_shouldReturnSuccessToPreventEnumeration() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Map<String, Object> result = authService.forgotPassword("unknown@example.com", "127.0.0.1", "TestAgent");

        assertNotNull(result.get("message"));
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void resetPassword_withValidToken_shouldResetAndNotify() {
        when(jwtService.isPasswordResetToken("valid-token")).thenReturn(true);
        when(jwtService.extractUsername("valid-token")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newpassword123")).thenReturn("new_encoded");

        Map<String, Object> result = authService.resetPassword(
                "valid-token", "newpassword123", "127.0.0.1", "TestAgent");

        assertEquals("Password has been reset successfully", result.get("message"));
        verify(userRepository).save(testUser);
        verify(emailService).sendAccountUpdateEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void resetPassword_withInvalidToken_shouldThrow() {
        when(jwtService.isPasswordResetToken("bad-token")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.resetPassword("bad-token", "newpass123", "127.0.0.1", "TestAgent"));
    }

    // ── Verify email tests ───────────────────────────────────

    @Test
    void verifyEmail_withValidToken_shouldActivateAccount() {
        testUser.setAccountStatus(AccountStatus.INACTIVE);
        when(jwtService.isEmailVerificationToken("verify-token")).thenReturn(true);
        when(jwtService.extractUsername("verify-token")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        Map<String, Object> result = authService.verifyEmail("verify-token");

        assertEquals(AccountStatus.ACTIVE, testUser.getAccountStatus());
        assertTrue(result.get("message").toString().contains("verified"));
        verify(userRepository).save(testUser);
        verify(emailService).sendWelcomeEmail(eq("test@example.com"), eq("testuser"));
    }

    @Test
    void verifyEmail_withInvalidToken_shouldThrow() {
        when(jwtService.isEmailVerificationToken("bad-token")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.verifyEmail("bad-token"));
    }

    @Test
    void verifyEmail_forAlreadyActiveAccount_shouldReturnAlreadyVerifiedMessage() {
        when(jwtService.isEmailVerificationToken("verify-token")).thenReturn(true);
        when(jwtService.extractUsername("verify-token")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        Map<String, Object> result = authService.verifyEmail("verify-token");

        assertTrue(result.get("message").toString().contains("already verified"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_forDisabledAccount_shouldThrow() {
        testUser.setAccountStatus(AccountStatus.DISABLED);
        when(jwtService.isEmailVerificationToken("verify-token")).thenReturn(true);
        when(jwtService.extractUsername("verify-token")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.verifyEmail("verify-token"));

        assertEquals(403, ex.getStatusCode().value());
    }

    // ── Resend verification tests ────────────────────────────

    @Test
    void resendVerification_withPendingAccount_shouldSendEmail() {
        testUser.setAccountStatus(AccountStatus.INACTIVE);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateEmailVerificationToken(testUser)).thenReturn("new-verify-token");

        Map<String, Object> result = authService.resendVerification("test@example.com");

        assertNotNull(result.get("message"));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), eq("testuser"), contains("new-verify-token"));
    }

    @Test
    void resendVerification_withActiveAccount_shouldNotSendEmail() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        authService.resendVerification("test@example.com");

        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    // ── Change password tests ────────────────────────────────

    @Test
    void changePassword_withCorrectOldPassword_shouldSucceed() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass123", "encoded_password")).thenReturn(true);
        when(passwordEncoder.encode("newpass1234")).thenReturn("new_encoded");

        Map<String, Object> result = authService.changePassword(
                "testuser", "oldpass123", "newpass1234", "127.0.0.1", "TestAgent");

        assertEquals("Password changed successfully", result.get("message"));
        verify(emailService).sendAccountUpdateEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    @Test
    void changePassword_withWrongOldPassword_shouldThrow() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "encoded_password")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.changePassword("testuser", "wrong", "newpass1234", "127.0.0.1", "TestAgent"));
    }

    @Test
    void changePassword_sameAsOld_shouldThrow() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("samepass1", "encoded_password")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.changePassword("testuser", "samepass1", "samepass1", "127.0.0.1", "TestAgent"));
    }

    @Test
    void changePassword_withShortNewPassword_shouldThrow() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass123", "encoded_password")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.changePassword("testuser", "oldpass123", "short", "127.0.0.1", "TestAgent"));
    }

    // ── Profile tests ────────────────────────────────────────

    @Test
    void getUserProfile_shouldReturnUserInfo() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        Map<String, Object> result = authService.getUserProfile("testuser");

        assertNotNull(result.get("user"));
    }

    @Test
    void updateProfile_shouldUpdateFieldsAndNotify() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name", null, null, null);

        Map<String, Object> result = authService.updateProfile(
                "testuser", request, "127.0.0.1", "TestAgent");

        assertEquals("Profile updated successfully", result.get("message"));
        assertEquals("Updated Name", testUser.getName());
        verify(emailService).sendAccountUpdateEmail(eq("test@example.com"), eq("testuser"), anyString());
    }

    // ── Refresh token tests ──────────────────────────────────

    @Test
    void refreshToken_withValidToken_shouldReturnNewTokens() {
        when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtService.extractUsername("valid-refresh")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(testUser)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(testUser)).thenReturn("new-refresh");

        Map<String, Object> result = authService.refreshToken("valid-refresh");

        assertEquals("new-access", result.get("accessToken"));
        assertEquals("new-refresh", result.get("refreshToken"));
    }

    @Test
    void refreshToken_withInvalidToken_shouldThrow() {
        when(jwtService.isRefreshToken("bad")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.refreshToken("bad"));
    }

    @Test
    void refreshToken_withInactiveUser_shouldThrow() {
        testUser.setAccountStatus(AccountStatus.INACTIVE);
        when(jwtService.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtService.extractUsername("valid-refresh")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        assertThrows(ResponseStatusException.class,
                () -> authService.refreshToken("valid-refresh"));
    }

    // ── Check availability tests ─────────────────────────────

    @Test
    void checkAvailability_username_available() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        Map<String, Object> result = authService.checkAvailability("username", "newuser");

        assertTrue((Boolean) result.get("available"));
    }

    @Test
    void checkAvailability_email_taken() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        Map<String, Object> result = authService.checkAvailability("email", "test@example.com");

        assertFalse((Boolean) result.get("available"));
    }

    @Test
    void checkAvailability_invalidField_shouldThrow() {
        assertThrows(ResponseStatusException.class,
                () -> authService.checkAvailability("invalid", "value"));
    }
}
