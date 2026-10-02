package com.consoleadda.dto.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserInfo user) {
}
