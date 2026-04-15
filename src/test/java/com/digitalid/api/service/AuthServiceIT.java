package com.digitalid.api.service;

import com.digitalid.api.AbstractIntegrationTest;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AuthService against a real MySQL database (Testcontainers).
 *
 * These tests verify actual persistence behavior: unique constraints, status transitions,
 * and the full register → verify → login lifecycle. They complement, not replace,
 * the unit tests in AuthServiceTest which mock collaborators and test branching logic.
 *
 * Naming: *IT.java → picked up by maven-failsafe-plugin (integration-test phase).
 * Run with: ./mvnw verify
 * Unit tests only: ./mvnw test
 */
class AuthServiceIT extends AbstractIntegrationTest {

    // EmailService is mocked: integration tests verify DB persistence and auth logic,
    // not email delivery (which requires an external SMTP server).
    // Mockito's default behavior (return null / do nothing) is appropriate here.
    @MockBean
    private EmailService emailService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    // Unique suffix per test to avoid cross-test constraint violations.
    // BeforeEach runs before each test method; each test gets its own username/email.
    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = String.valueOf(System.nanoTime());
    }

    // ── Register tests ───────────────────────────────────────────────────────

    @Test
    void registerUser_shouldPersistUserAsInactive() {
        RegisterRequest request = buildRegisterRequest("user_" + uniqueSuffix, uniqueSuffix + "@example.com");

        authService.registerUser(request, "127.0.0.1", "TestAgent");

        var saved = userRepository.findByUsername("user_" + uniqueSuffix);
        assertTrue(saved.isPresent(), "User should be persisted");
        assertEquals(AccountStatus.INACTIVE, saved.get().getAccountStatus(),
                "New users must start INACTIVE until email is verified");
        assertNull(saved.get().getEmailVerifiedAt(),
                "emailVerifiedAt must be null before verification");
    }

    @Test
    void registerUser_duplicateUsername_shouldThrow() {
        RegisterRequest first = buildRegisterRequest("dupuser_" + uniqueSuffix, "first_" + uniqueSuffix + "@example.com");
        RegisterRequest second = buildRegisterRequest("dupuser_" + uniqueSuffix, "second_" + uniqueSuffix + "@example.com");

        authService.registerUser(first, "127.0.0.1", "TestAgent");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(second, "127.0.0.1", "TestAgent"),
                "Duplicate username should throw");
        assertTrue(ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 409,
                "Expected 400 or 409, got: " + ex.getStatusCode().value());
    }

    @Test
    void registerUser_duplicateEmail_shouldThrow() {
        String sharedEmail = "shared_" + uniqueSuffix + "@example.com";
        RegisterRequest first = buildRegisterRequest("userA_" + uniqueSuffix, sharedEmail);
        RegisterRequest second = buildRegisterRequest("userB_" + uniqueSuffix, sharedEmail);

        authService.registerUser(first, "127.0.0.1", "TestAgent");

        assertThrows(ResponseStatusException.class,
                () -> authService.registerUser(second, "127.0.0.1", "TestAgent"),
                "Duplicate email should throw");
    }

    // ── Email verify → login lifecycle ───────────────────────────────────────

    @Test
    void verifyEmail_shouldActivateAccountAndAllowLogin() {
        String username = "verify_" + uniqueSuffix;
        String email = uniqueSuffix + "_v@example.com";
        String password = "Password123!";

        RegisterRequest request = buildRegisterRequest(username, email, password);
        authService.registerUser(request, "127.0.0.1", "TestAgent");

        // Generate the same token the service would send in the email
        var user = userRepository.findByUsername(username).orElseThrow();
        String token = jwtService.generateEmailVerificationToken(user);

        authService.verifyEmail(token);

        var activated = userRepository.findByUsername(username).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, activated.getAccountStatus(),
                "Account should be ACTIVE after email verification");
        assertNotNull(activated.getEmailVerifiedAt(),
                "emailVerifiedAt should be set after verification");
    }

    @Test
    void login_withUnverifiedAccount_shouldThrow403() {
        String username = "unverified_" + uniqueSuffix;
        String password = "Password123!";

        authService.registerUser(
                buildRegisterRequest(username, uniqueSuffix + "_u@example.com", password),
                "127.0.0.1", "TestAgent");

        // Do NOT verify email — account remains INACTIVE
        LoginRequest loginRequest = new LoginRequest(username, null, null, password);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.userLogin(loginRequest, "127.0.0.1", "TestAgent"),
                "Login should be blocked for INACTIVE account");
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void fullCycle_register_verify_login_shouldSucceed() {
        String username = "cycle_" + uniqueSuffix;
        String email = uniqueSuffix + "_c@example.com";
        String password = "Password123!";

        // 1. Register
        authService.registerUser(buildRegisterRequest(username, email, password),
                "127.0.0.1", "TestAgent");

        // 2. Verify email
        var user = userRepository.findByUsername(username).orElseThrow();
        String token = jwtService.generateEmailVerificationToken(user);
        authService.verifyEmail(token);

        // 3. Login — should return access + refresh tokens
        var loginResult = authService.userLogin(
                new LoginRequest(username, null, null, password),
                "127.0.0.1", "TestAgent");

        assertNotNull(loginResult.get("accessToken"), "accessToken should be present");
        assertNotNull(loginResult.get("refreshToken"), "refreshToken should be present");
        assertEquals("Bearer", loginResult.get("tokenType"));

        // 4. Assert last login timestamp was updated
        var loggedIn = userRepository.findByUsername(username).orElseThrow();
        assertNotNull(loggedIn.getLastLoginAt(), "lastLoginAt should be set after login");
        assertEquals(0, loggedIn.getFailedLoginAttempts(),
                "Failed attempts should be reset to 0 after successful login");
    }

    @Test
    void login_withWrongPassword_shouldIncrementFailedAttemptsInDb() {
        String username = "fail_" + uniqueSuffix;
        String email = uniqueSuffix + "_f@example.com";
        String password = "Password123!";

        // Register and verify so account is ACTIVE
        authService.registerUser(buildRegisterRequest(username, email, password),
                "127.0.0.1", "TestAgent");
        var user = userRepository.findByUsername(username).orElseThrow();
        authService.verifyEmail(jwtService.generateEmailVerificationToken(user));

        // Attempt login with wrong password
        assertThrows(ResponseStatusException.class,
                () -> authService.userLogin(
                        new LoginRequest(username, null, null, "WrongPassword!"),
                        "127.0.0.1", "TestAgent"));

        var afterFail = userRepository.findByUsername(username).orElseThrow();
        assertEquals(1, afterFail.getFailedLoginAttempts(),
                "Failed login attempts should be incremented and persisted to DB");
    }

    @Test
    void resendVerification_forInactiveAccount_shouldNotThrow() {
        String email = uniqueSuffix + "_r@example.com";
        authService.registerUser(
                buildRegisterRequest("resend_" + uniqueSuffix, email),
                "127.0.0.1", "TestAgent");

        // Resend should succeed silently even if mail fails (SMTP not configured in test)
        assertDoesNotThrow(() -> authService.resendVerification(email));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String username, String email) {
        return buildRegisterRequest(username, email, "Password123!");
    }

    private RegisterRequest buildRegisterRequest(String username, String email, String password) {
        // Derive a unique 10-digit phone number from the nanosecond uniqueSuffix.
        // AuthService.validatePhoneno requires a non-null phone number.
        long phoneNo = 1_000_000_000L + (Long.parseLong(uniqueSuffix) % 9_000_000_000L);
        return new RegisterRequest(
                username,
                "Test User",
                email,
                phoneNo,
                LocalDate.of(1995, 6, 15),
                Gender.MALE,
                Role.USER,
                password,
                true            // termsAccepted
        );
    }
}
