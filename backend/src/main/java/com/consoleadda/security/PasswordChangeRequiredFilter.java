package com.consoleadda.security;

import com.consoleadda.config.AppProperties;
import com.consoleadda.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * While a user is flagged {@code must_change_password}, only the endpoints needed to change the password
 * are allowed. Everything else returns 403 {@code PASSWORD_CHANGE_REQUIRED}.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of("/api/auth/me", "/api/auth/change-password");

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean blocked = properties.security().enforcePasswordChange()
                && CurrentUser.get().map(AuthenticatedUser::mustChangePassword).orElse(false)
                && !ALLOWED_PATHS.contains(request.getRequestURI());
        if (!blocked) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.body(HttpStatus.FORBIDDEN,
                "PASSWORD_CHANGE_REQUIRED", "You must change your password before continuing",
                request.getRequestURI(), List.of()));
    }
}
