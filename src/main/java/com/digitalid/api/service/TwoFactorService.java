package com.digitalid.api.service;

import com.digitalid.api.controller.models.User;
import com.digitalid.api.repositroy.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
public class TwoFactorService {

    private static final String ISSUER = "Digital ID";

    private final UserRepository userRepository;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    public TwoFactorService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Generate a new TOTP secret and return the QR code otpauth:// URI. */
    public Map<String, Object> setupTwoFactor(String username) {
        User user = getUser(username);
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-factor authentication is already enabled");
        }
        String secret = secretGenerator.generate();
        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("secret", secret);
        response.put("qrUri", qrData.getUri());
        return response;
    }

    /** Verify the provided TOTP code and enable 2FA if valid. */
    public Map<String, Object> enableTwoFactor(String username, String code) {
        User user = getUser(username);
        if (user.getTwoFactorSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call /2fa/setup first to generate a secret");
        }
        if (!codeVerifier.isValidCode(user.getTwoFactorSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid authentication code");
        }
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Two-factor authentication enabled successfully");
        response.put("twoFactorEnabled", true);
        return response;
    }

    /** Disable 2FA for the user (requires current TOTP code or password). */
    public Map<String, Object> disableTwoFactor(String username, String code) {
        User user = getUser(username);
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor authentication is not enabled");
        }
        if (!codeVerifier.isValidCode(user.getTwoFactorSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid authentication code");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Two-factor authentication disabled");
        response.put("twoFactorEnabled", false);
        return response;
    }

    /** Verify a TOTP code during login (used when 2FA is enabled). */
    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
