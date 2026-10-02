package com.consoleadda.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Typed application configuration (prefix {@code app}). Secrets come from environment variables;
 * see {@code .env.example}.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId timezone,
        @Valid @NotNull Security security,
        @Valid @NotNull Bootstrap bootstrap,
        List<String> corsAllowedOrigins
) {

    public record Security(
            @NotBlank String jwtSecret,
            @NotNull Duration jwtTtl,
            @NotBlank String jwtIssuer,
            boolean enforcePasswordChange
    ) {
    }

    /**
     * Users created at startup when they do not exist yet. Used for development accounts and for the
     * first production admin (whose password comes from an environment variable).
     */
    public record Bootstrap(List<@Valid BootstrapUser> users) {
        public List<BootstrapUser> usersOrEmpty() {
            return users == null ? List.of() : users;
        }
    }

    public record BootstrapUser(
            @NotBlank String username,
            String password,
            @NotBlank String fullName,
            @NotBlank String role,
            boolean mustChangePassword
    ) {
    }
}
