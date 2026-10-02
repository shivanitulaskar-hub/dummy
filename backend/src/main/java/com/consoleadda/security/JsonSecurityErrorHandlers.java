package com.consoleadda.security;

import com.consoleadda.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Writes 401/403 responses raised by the security filter chain in the standard {@code ApiError} format. */
@Slf4j
@Component
public class JsonSecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            if (request.getHeader("Authorization") != null) {
                log.warn("Rejected bearer token on {} {} from {}: {}", request.getMethod(), request.getRequestURI(),
                        request.getRemoteAddr(), ex.getMessage());
            }
            write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Your session has expired or is invalid. Please log in again.");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                       String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                GlobalExceptionHandler.body(status, code, message, request.getRequestURI(), List.of()));
    }
}
