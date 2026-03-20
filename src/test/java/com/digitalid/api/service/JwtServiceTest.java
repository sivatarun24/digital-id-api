package com.digitalid.api.service;

import com.digitalid.api.controller.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test_secret_key_that_is_at_least_32_bytes_long");
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .role(Role.USER)
                .build();
    }

    @Test
    void generateToken_shouldReturnNonNullToken() {
        String token = jwtService.generateToken(testUser);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractUsername_shouldReturnCorrectUsername() {
        String token = jwtService.generateToken(testUser);
        String username = jwtService.extractUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(testUser);
        assertTrue(jwtService.isTokenValid(token, "testuser"));
    }

    @Test
    void isTokenValid_shouldReturnFalseForWrongUsername() {
        String token = jwtService.generateToken(testUser);
        assertFalse(jwtService.isTokenValid(token, "otheruser"));
    }

    @Test
    void isTokenValid_shouldReturnFalseForInvalidToken() {
        assertFalse(jwtService.isTokenValid("invalid.token.here", "testuser"));
    }

    @Test
    void generateRefreshToken_shouldBeIdentifiedAsRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser);
        assertTrue(jwtService.isRefreshToken(refreshToken));
    }

    @Test
    void generateToken_shouldNotBeIdentifiedAsRefreshToken() {
        String accessToken = jwtService.generateToken(testUser);
        assertFalse(jwtService.isRefreshToken(accessToken));
    }

    @Test
    void generatePasswordResetToken_shouldBeIdentifiedAsResetToken() {
        String resetToken = jwtService.generatePasswordResetToken(testUser);
        assertTrue(jwtService.isPasswordResetToken(resetToken));
    }

    @Test
    void generateToken_shouldNotBeIdentifiedAsResetToken() {
        String accessToken = jwtService.generateToken(testUser);
        assertFalse(jwtService.isPasswordResetToken(accessToken));
    }

    @Test
    void isRefreshToken_shouldReturnFalseForGarbage() {
        assertFalse(jwtService.isRefreshToken("not-a-token"));
    }

    @Test
    void isPasswordResetToken_shouldReturnFalseForGarbage() {
        assertFalse(jwtService.isPasswordResetToken("not-a-token"));
    }
}
