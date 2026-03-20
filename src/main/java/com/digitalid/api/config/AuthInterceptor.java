package com.digitalid.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle (
            HttpServletRequest request,
            HttpServletResponse response,
            Object filter
    ) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (request.getServletPath().equals("/api/auth/**")) {
            return true;
        }

        if (auth == null || !auth.isAuthenticated()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthenticated");
            return false;
        }
        return true;
    }
}
