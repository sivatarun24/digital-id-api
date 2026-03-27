package com.digitalid.api.config;

import com.digitalid.api.controller.models.DeveloperApp;
import com.digitalid.api.service.DeveloperAppService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts /api/verify requests and validates the X-API-Key header.
 * Modelled on ManagementSecretFilter — same OncePerRequestFilter pattern.
 * On success, stores the authenticated DeveloperApp as a request attribute.
 */
@Component
@Order(2)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String VERIFY_PATH    = "/api/verify";
    private static final String DEV_ME_PATH    = "/api/developers/me";

    private final DeveloperAppService developerAppService;

    public ApiKeyFilter(DeveloperAppService developerAppService) {
        this.developerAppService = developerAppService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (requiresApiKey(path)) {
            String rawKey = request.getHeader(API_KEY_HEADER);
            if (rawKey == null || rawKey.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"X-API-Key header is required\"}");
                return;
            }

            try {
                DeveloperApp app = developerAppService.authenticate(rawKey);
                // Store authenticated app in request attribute for controller access
                request.setAttribute("authenticatedApp", app);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresApiKey(String path) {
        if (path == null) return false;
        // Use exact match — startsWith("/api/verify") would also catch /api/verify-identity
        return path.equals(VERIFY_PATH) || path.startsWith(DEV_ME_PATH);
    }
}
