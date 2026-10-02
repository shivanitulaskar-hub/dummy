package com.consoleadda.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the authenticated user for services (audit fields, created_by, etc.). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }
}
