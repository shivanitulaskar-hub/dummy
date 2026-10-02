package com.consoleadda.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 72) String password
) {

    /** Never print the password, even in debug logs. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
