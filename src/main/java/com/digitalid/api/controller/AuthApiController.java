package com.digitalid.api.controller;

import com.digitalid.api.controller.models.*;
import com.digitalid.api.metrics.ApiMetrics;
import com.digitalid.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthApiController {

    private final AuthService authService;
    private final ApiMetrics metrics;
    private final com.digitalid.api.service.TwoFactorService twoFactorService;

    public AuthApiController(AuthService authService, ApiMetrics metrics,
                             com.digitalid.api.service.TwoFactorService twoFactorService) {
        this.authService = authService;
        this.metrics = metrics;
        this.twoFactorService = twoFactorService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest,
                                                     HttpServletRequest httpRequest) {
        if (loginRequest == null || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Password is required"));
        }

        boolean hasIdentifier =
                (loginRequest.getUsername() != null && !loginRequest.getUsername().isBlank())
                || (loginRequest.getEmail() != null && !loginRequest.getEmail().isBlank())
                || (loginRequest.getPhoneNo() != null && !loginRequest.getPhoneNo().isBlank());

        if (!hasIdentifier) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username, email, or phone number is required"));
        }

        Map<String, Object> loginResponse = authService.userLogin(loginRequest,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest registerRequest,
                                                        HttpServletRequest httpRequest) {
        if (registerRequest == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Request body is required"));
        }

        Map<String, Object> response = authService.registerUser(registerRequest,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/refresh-token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request != null ? request.get("refreshToken") : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token is required"));
        }

        Map<String, Object> response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/logout")
    public ResponseEntity<Map<String, Object>> logout(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        metrics.recordLogout();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping(value = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        if (request == null
                || request.getOldPassword() == null || request.getOldPassword().isBlank()
                || request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Old password and new password are required"));
        }

        Map<String, Object> response = authService.changePassword(
                authentication.getName(), request.getOldPassword(), request.getNewPassword(),
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/update-profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        if (request == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Request body is required"));
        }

        Map<String, Object> response = authService.updateProfile(authentication.getName(), request,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Verification token is required"));
        }
        Map<String, Object> response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/resend-verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resendVerification(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email is required"));
        }
        Map<String, Object> response = authService.resendVerification(email.trim());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody ForgotPasswordRequest request,
                                                              HttpServletRequest httpRequest) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email is required"));
        }

        Map<String, Object> response = authService.forgotPassword(request.getEmail(),
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody ResetPasswordRequest request,
                                                             HttpServletRequest httpRequest) {
        if (request == null
                || request.getToken() == null || request.getToken().isBlank()
                || request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Token and new password are required"));
        }

        Map<String, Object> response = authService.resetPassword(request.getToken(), request.getNewPassword(),
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping(value = "/account", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteAccount(
            Authentication authentication,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
        }
        String password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Password is required to confirm account deletion"));
        }
        Map<String, Object> response = authService.deleteAccount(
                authentication.getName(), password,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestParam String field,
            @RequestParam String value) {
        if (field == null || field.isBlank() || value == null || value.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Field and value query parameters are required"));
        }

        Map<String, Object> response = authService.checkAvailability(field, value);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        Map<String, Object> profile = authService.getUserProfile(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/2fa/setup")
    public ResponseEntity<Map<String, Object>> twoFactorSetup(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
        }
        return ResponseEntity.ok(twoFactorService.setupTwoFactor(authentication.getName()));
    }

    @PostMapping(value = "/2fa/enable", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> twoFactorEnable(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
        }
        String code = body != null ? body.get("code") : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Authenticator code is required"));
        }
        return ResponseEntity.ok(twoFactorService.enableTwoFactor(authentication.getName(), code.trim()));
    }

    @PostMapping(value = "/2fa/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> twoFactorDisable(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
        }
        String code = body != null ? body.get("code") : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Authenticator code is required"));
        }
        return ResponseEntity.ok(twoFactorService.disableTwoFactor(authentication.getName(), code.trim()));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
