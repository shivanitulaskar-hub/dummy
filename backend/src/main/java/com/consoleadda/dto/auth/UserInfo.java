package com.consoleadda.dto.auth;

import com.consoleadda.entity.RoleCode;

public record UserInfo(Long id, String username, String fullName, RoleCode role, boolean mustChangePassword) {
}
