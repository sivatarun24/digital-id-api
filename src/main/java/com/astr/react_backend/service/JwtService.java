package com.astr.react_backend.service;

import com.astr.react_backend.controller.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;

    private static final long ACCESS_TOKEN_EXPIRY  = 1000 * 60 * 60;           // 1 hour
    private static final long REFRESH_TOKEN_EXPIRY = 1000L * 60 * 60 * 24 * 7; // 7 days
    private static final long RESET_TOKEN_EXPIRY   = 1000 * 60 * 15;           // 15 minutes

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("purpose", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY))
                .signWith(secretKey)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "refresh".equals(claims.get("purpose", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String generatePasswordResetToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("purpose", "password-reset")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + RESET_TOKEN_EXPIRY))
                .signWith(secretKey)
                .compact();
    }

    public boolean isPasswordResetToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "password-reset".equals(claims.get("purpose", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        try {
            String name = extractUsername(token);
            return name.equals(username) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
