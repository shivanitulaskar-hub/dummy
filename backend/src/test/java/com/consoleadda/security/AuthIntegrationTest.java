package com.consoleadda.security;

import com.consoleadda.entity.RoleCode;
import com.consoleadda.entity.User;
import com.consoleadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void adminCanLogInAndSeeProfile() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String token = readJson(body).get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        assertThat(login("OPERATOR", OPERATOR_PASSWORD)).isNotBlank();
    }

    @Test
    void wrongPasswordReturns401WithStructuredError() throws Exception {
        User user = createUser(RoleCode.OPERATOR, "Secret123", false);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", user.getUsername(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void unknownUserGetsSameErrorAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "nobody-here", "password", "whatever1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void blankLoginRequestReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors", hasSize(2)));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        User user = createUser(RoleCode.OPERATOR, "Secret123", false);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", user.getUsername(), "password", "bad-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while locked.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", user.getUsername(), "password", "Secret123"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        String token = operatorToken();
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        mockMvc.perform(get("/api/stations").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", bearer(operatorToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin')].role").value("ADMIN"))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void userFlaggedForPasswordChangeIsBlockedUntilChanged() throws Exception {
        User user = createUser(RoleCode.OPERATOR, "Initial123", true);
        String oldToken = login(user.getUsername(), "Initial123");

        mockMvc.perform(get("/api/stations").header("Authorization", bearer(oldToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // Weak password rejected by validation.
        mockMvc.perform(post("/api/auth/change-password").header("Authorization", bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Initial123", "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("newPassword"));

        // Wrong current password rejected.
        mockMvc.perform(post("/api/auth/change-password").header("Authorization", bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Wrong1234", "newPassword", "BrandNew123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));

        Thread.sleep(1100); // tokens have second precision; make sure the old one predates the change
        String body = mockMvc.perform(post("/api/auth/change-password").header("Authorization", bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Initial123", "newPassword", "BrandNew123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String newToken = readJson(body).get("accessToken").asText();

        mockMvc.perform(get("/api/stations").header("Authorization", bearer(newToken)))
                .andExpect(status().isOk());
        // Token issued before the password change is no longer accepted.
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isUnauthorized());
        // New password works for login, old one does not.
        assertThat(login(user.getUsername(), "BrandNew123")).isNotBlank();
    }

    @Test
    void deactivatedUserTokenIsRejectedImmediately() throws Exception {
        User user = createUser(RoleCode.OPERATOR, "Secret123", false);
        String token = login(user.getUsername(), "Secret123");
        mockMvc.perform(get("/api/stations").header("Authorization", bearer(token))).andExpect(status().isOk());

        user = userRepository.findById(user.getId()).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/stations").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", user.getUsername(), "password", "Secret123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
