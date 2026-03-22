package com.digitalid.api.service;

import com.digitalid.api.audit.AuditAction;
import com.digitalid.api.audit.AuditLogService;
import com.digitalid.api.controller.models.*;
import com.digitalid.api.metrics.ApiMetrics;
import com.digitalid.api.repositroy.UserRepository;
import com.digitalid.api.service.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final ApiMetrics metrics;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, EmailService emailService,
                       AuditLogService auditLogService, ApiMetrics metrics) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.metrics = metrics;
    }

    public Map<String, Object> userLogin(LoginRequest loginRequest, String ipAddress, String userAgent) {
        return metrics.timeLogin(() -> {
            User user = resolveUser(loginRequest);

            if (user.getAccountStatus() == AccountStatus.INACTIVE) {
                metrics.recordLoginFailure();
                auditLogService.log(user.getUsername(), AuditAction.LOGIN_FAILED,
                        "Email not verified", ipAddress, userAgent);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email address before signing in. Check your inbox for the verification link.");
            }

            if (user.getAccountStatus() == AccountStatus.DISABLED) {
                metrics.recordLoginFailure();
                auditLogService.log(user.getUsername(), AuditAction.LOGIN_FAILED,
                        "Account disabled", ipAddress, userAgent);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account has been suspended. Please contact support.");
            }

            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
                user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
                userRepository.save(user);
                metrics.recordLoginFailure();
                auditLogService.log(user.getUsername(), AuditAction.LOGIN_FAILED,
                        "Invalid password, attempts=" + user.getFailedLoginAttempts(), ipAddress, userAgent);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            user.setFailedLoginAttempts(0);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            metrics.recordLoginSuccess();
            auditLogService.log(user.getUsername(), AuditAction.LOGIN, "Login successful", ipAddress, userAgent);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", 60 * 60);
            response.put("user", buildUserInfo(user));
            return response;
        });
    }

    private User resolveUser(LoginRequest loginRequest) {
        if (loginRequest.getUsername() != null && !loginRequest.getUsername().isBlank()) {
            return userRepository.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        }

        if (loginRequest.getEmail() != null && !loginRequest.getEmail().isBlank()) {
            return userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        }

        if (loginRequest.getPhoneNo() != null && !loginRequest.getPhoneNo().isBlank()) {
            try {
                Long phoneNo = Long.parseLong(loginRequest.getPhoneNo());
                return userRepository.findByPhoneNo(phoneNo)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone number format");
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username, email, or phone number is required");
    }

    public Map<String, Object> registerUser(RegisterRequest registerRequest, String ipAddress, String userAgent) {
        return metrics.timeRegister(() -> {
            if (registerRequest.getTermsAccepted() == null || !registerRequest.getTermsAccepted()) {
                metrics.recordRegisterFailure();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You must accept the Terms of Service and Privacy Policy to create an account");
            }
            if (registerRequest.getPassword() == null || registerRequest.getPassword().isBlank()) {
                metrics.recordRegisterFailure();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be null/empty");
            }
            if (registerRequest.getPassword().length() < 8) {
                metrics.recordRegisterFailure();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
            }
            if (registerRequest.getName() == null || registerRequest.getName().isBlank()) {
                metrics.recordRegisterFailure();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be null/empty");
            }

            validateUsername(registerRequest.getUsername());
            validateEmail(registerRequest.getEmail());
            validatePhoneno(registerRequest.getPhoneNo());

            LocalDateTime now = LocalDateTime.now();
            User user = User.builder()
                    .username(registerRequest.getUsername())
                    .name(registerRequest.getName())
                    .email(registerRequest.getEmail())
                    .phoneNo(registerRequest.getPhoneNo())
                    .dateOfBirth(registerRequest.getDateOfBirth())
                    .gender(registerRequest.getGender())
                    .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                    .passwordUpdatedAt(now)
                    .role(registerRequest.getRole())
                    .termsAcceptedAt(now)
                    .privacyPolicyAcceptedAt(now)
                    .build();

            userRepository.save(user);

            metrics.recordRegisterSuccess();
            auditLogService.log(user.getUsername(), AuditAction.REGISTER, "User registered", ipAddress, userAgent);

            String verificationToken = jwtService.generateEmailVerificationToken(user);
            String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;
            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationLink);
            } catch (Exception e) {
                log.warn("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Account created. Please check your email to verify your account before signing in.");
            return response;
        });
    }

    public Map<String, Object> forgotPassword(String email, String ipAddress, String userAgent) {
        // Always return success to prevent email enumeration
        Map<String, Object> response = new HashMap<>();
        response.put("message", "If an account with that email exists, a password reset link has been sent.");

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getAccountStatus() == AccountStatus.DISABLED) {
                return; // Silent — don't reveal account status
            }
            String resetToken = jwtService.generatePasswordResetToken(user);
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

            metrics.recordPasswordResetRequest();
            auditLogService.log(user.getUsername(), AuditAction.PASSWORD_RESET_REQUEST,
                    "Password reset requested", ipAddress, userAgent);

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
            } catch (Exception e) {
                log.warn("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
            }
        });

        return response;
    }

    public Map<String, Object> resetPassword(String token, String newPassword, String ipAddress, String userAgent) {
        if (!jwtService.isPasswordResetToken(token)) {
            metrics.recordPasswordResetFailure();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        metrics.recordPasswordResetSuccess();
        auditLogService.log(user.getUsername(), AuditAction.PASSWORD_RESET,
                "Password reset completed", ipAddress, userAgent);

        try {
            emailService.sendAccountUpdateEmail(user.getEmail(), user.getUsername(),
                    "Your password was reset successfully. If you did not perform this action, contact support immediately.");
        } catch (Exception e) {
            log.warn("Failed to send password reset confirmation to {}: {}", user.getEmail(), e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password has been reset successfully");
        return response;
    }

    public Map<String, Object> verifyEmail(String token) {
        if (!jwtService.isEmailVerificationToken(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification link. Please request a new one.");
        }

        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification link.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Email already verified. You can sign in.");
            return response;
        }

        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended.");
        }

        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.log(user.getUsername(), AuditAction.REGISTER, "Email verified");

        try {
            emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Email verified successfully. You can now sign in.");
        return response;
    }

    public Map<String, Object> resendVerification(String email) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "If your account is pending verification, a new link has been sent.");

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getAccountStatus() != AccountStatus.INACTIVE) return;

            String token = jwtService.generateEmailVerificationToken(user);
            String link = frontendUrl + "/verify-email?token=" + token;
            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), link);
            } catch (Exception e) {
                log.warn("Failed to resend verification email to {}: {}", user.getEmail(), e.getMessage());
            }
        });

        return response;
    }

    public Map<String, Object> getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("user", buildUserInfo(user));
        return response;
    }

    public Map<String, Object> refreshToken(String refreshToken) {
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        String username;
        try {
            username = jwtService.extractUsername(refreshToken);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not active");
        }

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        metrics.recordTokenRefresh();
        auditLogService.log(username, AuditAction.TOKEN_REFRESH, "Token refreshed");

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("refreshToken", newRefreshToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", 60 * 60);
        return response;
    }

    public Map<String, Object> changePassword(String username, String oldPassword, String newPassword,
                                               String ipAddress, String userAgent) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            metrics.recordPasswordChangeFailure();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (oldPassword.equals(newPassword)) {
            metrics.recordPasswordChangeFailure();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from old password");
        }

        if (newPassword.length() < 8) {
            metrics.recordPasswordChangeFailure();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        metrics.recordPasswordChangeSuccess();
        auditLogService.log(username, AuditAction.PASSWORD_CHANGE,
                "Password changed", ipAddress, userAgent);

        try {
            emailService.sendAccountUpdateEmail(user.getEmail(), user.getUsername(),
                    "Your password was changed successfully. If you did not make this change, please reset your password immediately.");
        } catch (Exception e) {
            log.warn("Failed to send password change confirmation to {}: {}", user.getEmail(), e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Password changed successfully");
        return response;
    }

    public Map<String, Object> updateProfile(String username, UpdateProfileRequest request,
                                              String ipAddress, String userAgent) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        StringBuilder changes = new StringBuilder();
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
            changes.append("name ");
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
            changes.append("dateOfBirth ");
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
            changes.append("gender ");
        }
        if (request.getMarketingOptIn() != null) {
            user.setMarketingOptIn(request.getMarketingOptIn());
            changes.append("marketingOptIn ");
        }

        userRepository.save(user);

        metrics.recordProfileUpdate();
        auditLogService.log(username, AuditAction.PROFILE_UPDATE,
                "Fields updated: " + changes.toString().trim(), ipAddress, userAgent);

        try {
            emailService.sendAccountUpdateEmail(user.getEmail(), user.getUsername(),
                    "Your profile information was updated.");
        } catch (Exception e) {
            log.warn("Failed to send profile update email to {}: {}", user.getEmail(), e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Profile updated successfully");
        response.put("user", buildUserInfo(user));
        return response;
    }

    public Map<String, Object> deleteAccount(String username, String password,
                                               String ipAddress, String userAgent) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect password");
        }

        auditLogService.log(username, AuditAction.ACCOUNT_DELETE, "Account deleted", ipAddress, userAgent);
        userRepository.delete(user);

        try {
            emailService.sendAccountUpdateEmail(user.getEmail(), user.getUsername(),
                    "Your Digital ID account has been permanently deleted. All associated data has been removed.");
        } catch (Exception e) {
            log.warn("Failed to send account deletion email to {}: {}", user.getEmail(), e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Account deleted successfully");
        return response;
    }

    public Map<String, Object> checkAvailability(String field, String value) {
        if (field == null || field.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field and value are required");
        }

        boolean taken;
        switch (field.toLowerCase()) {
            case "username":
                taken = userRepository.existsByUsername(value);
                break;
            case "email":
                taken = userRepository.existsByEmail(value);
                break;
            case "phoneno":
                try {
                    taken = userRepository.existsByPhoneNo(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone number format");
                }
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid field. Allowed values: username, email, phoneno");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("field", field);
        response.put("value", value);
        response.put("available", !taken);
        if (taken) {
            response.put("message", field + " is already taken");
        }
        return response;
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be null/empty");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be between 3 and 50 characters");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be null/empty");
        }
        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
    }

    private void validatePhoneno(Long phoneNo) {
        if (phoneNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PhoneNo cannot be null/empty");
        }
        String phoneStr = String.valueOf(phoneNo);
        if (phoneStr.length() < 7 || phoneStr.length() > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number must be between 7 and 15 digits");
        }
        if (userRepository.existsByPhoneNo(phoneNo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PhoneNo already exists");
        }
    }

    private Map<String, Object> buildUserInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("name", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("phoneNo", user.getPhoneNo());
        userInfo.put("dateOfBirth", user.getDateOfBirth());
        userInfo.put("gender", user.getGender());
        userInfo.put("role", user.getRole());
        userInfo.put("accountStatus", user.getAccountStatus());
        userInfo.put("lastLoginAt", user.getLastLoginAt());
        userInfo.put("twoFactorEnabled", Boolean.TRUE.equals(user.getTwoFactorEnabled()));
        return userInfo;
    }
}
