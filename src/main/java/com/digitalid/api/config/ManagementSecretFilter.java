package com.digitalid.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * When MANAGEMENT_SECRET is set, only requests with header X-Management-Secret matching it
 * can access /actuator/prometheus and /actuator/metrics (metrics not exposed publicly).
 */
@Component
@Order(1)
public class ManagementSecretFilter extends OncePerRequestFilter {

    private static final String MANAGEMENT_SECRET_HEADER = "X-Management-Secret";

    @Value("${app.management.secret:}")
    private String managementSecret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isMetricsPath(path) && managementSecret != null && !managementSecret.isBlank()) {
            String header = request.getHeader(MANAGEMENT_SECRET_HEADER);
            if (!managementSecret.equals(header)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isMetricsPath(String path) {
        return path != null && (path.endsWith("/actuator/prometheus") || path.endsWith("/actuator/metrics"));
    }
}
