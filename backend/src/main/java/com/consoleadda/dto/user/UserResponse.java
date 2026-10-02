package com.consoleadda.dto.user;

import com.consoleadda.entity.RoleCode;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        RoleCode role,
        boolean active,
        boolean mustChangePassword,
        Instant lastLoginAt
) {
}
