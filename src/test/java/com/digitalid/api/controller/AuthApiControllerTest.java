package com.digitalid.api.controller;

import com.digitalid.api.controller.models.*;
import com.digitalid.api.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    @Test
    void login_withValidRequest_shouldReturn200() throws Exception {
        LoginRequest request = new LoginRequest("testuser", null, null, "password123");
        Map<String, Object> mockResponse = Map.of(
                "accessToken", "token",
                "refreshToken", "refresh",
                "tokenType", "Bearer",
                "expiresIn", 3600
        );

        when(authService.userLogin(any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_withNoPassword_shouldReturn400() throws Exception {
        LoginRequest request = new LoginRequest("testuser", null, null, null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password is required"));
    }

    @Test
    void login_withNoIdentifier_shouldReturn400() throws Exception {
        LoginRequest request = new LoginRequest(null, null, null, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_withValidRequest_shouldReturn200() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser", "New User", "new@example.com",
                1234567890L, null, Gender.MALE, Role.USER, "password123", true);

        Map<String, Object> mockResponse = Map.of("message", "Registration successful");
        when(authService.registerUser(any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration successful"));
    }

    @Test
    void forgotPassword_withValidEmail_shouldReturn200() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");
        Map<String, Object> mockResponse = Map.of("message", "Password reset link has been sent");
        when(authService.forgotPassword(any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_withNoEmail_shouldReturn400() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest(null);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_withValidRequest_shouldReturn200() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "newpassword123");
        Map<String, Object> mockResponse = Map.of("message", "Password has been reset successfully");
        when(authService.resetPassword(any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    void requestChangePasswordOtp_authenticated_shouldReturn200() throws Exception {
        RequestPasswordChangeOtpRequest request = new RequestPasswordChangeOtpRequest("oldpass123");
        Map<String, Object> mockResponse = Map.of("message", "A verification code has been sent to your email address.");
        when(authService.requestPasswordChangeOtp(any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/change-password/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    void changePassword_authenticated_shouldReturn200() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldpass123", "newpass1234", "newpass1234", "123456");
        Map<String, Object> mockResponse = Map.of("message", "Password changed successfully");
        when(authService.changePassword(any(), any(), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateProfile_authenticated_shouldReturn200() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", null, null, null);
        Map<String, Object> mockResponse = Map.of("message", "Profile updated successfully");
        when(authService.updateProfile(any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(put("/api/auth/update-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void currentUser_authenticated_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of("user", Map.of("username", "testuser"));
        when(authService.getUserProfile("testuser")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    void checkAvailability_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of("available", true, "field", "username", "value", "newuser");
        when(authService.checkAvailability("username", "newuser")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/auth/check-availability")
                        .param("field", "username")
                        .param("value", "newuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void refreshToken_withValidToken_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of(
                "accessToken", "new-token", "refreshToken", "new-refresh");
        when(authService.refreshToken("valid-refresh")).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"valid-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-token"));
    }

    @Test
    void refreshToken_withNoToken_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void logout_authenticated_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void logout_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyEmail_withValidToken_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of("message", "Email verified successfully. You can now sign in.");
        when(authService.verifyEmail("valid-token")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void verifyEmail_withNoToken_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_withValidEmail_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of("message", "If your account is pending verification, a new link has been sent.");
        when(authService.resendVerification("user@example.com")).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resendVerification_withNoEmail_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
