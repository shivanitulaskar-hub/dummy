package com.consoleadda.security;

import com.consoleadda.entity.RoleCode;

/**
 * The principal stored in the security context for every authenticated API request.
 * Built from the database on each request, so deactivation and role changes apply immediately.
 */
public record AuthenticatedUser(Long id, String username, String fullName, RoleCode role, boolean mustChangePassword) {

    public boolean isAdmin() {
        return role == RoleCode.ADMIN;
    }
}
