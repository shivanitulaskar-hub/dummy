package com.consoleadda.support;

import com.consoleadda.entity.RoleCode;
import com.consoleadda.entity.User;
import com.consoleadda.repository.RoleRepository;
import com.consoleadda.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: full Spring context against a real MySQL 8.4 (Testcontainers),
 * with the real Flyway migrations. One container is shared by all test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final String ADMIN_PASSWORD = "Admin@1234";
    public static final String OPERATOR_PASSWORD = "Operator@1234";

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("console_adda")
            .withUsername("console_adda")
            .withPassword("test-password");

    static {
        MYSQL.start();
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return login("admin", ADMIN_PASSWORD);
    }

    protected String operatorToken() throws Exception {
        return login("operator", OPERATOR_PASSWORD);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    /** Creates an isolated user so tests that lock/deactivate/change passwords don't affect each other. */
    protected User createUser(RoleCode role, String password, boolean mustChangePassword) {
        User user = new User();
        user.setUsername("u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setFullName("Test User");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(roleRepository.findByCode(role).orElseThrow());
        user.setActive(true);
        user.setMustChangePassword(mustChangePassword);
        return userRepository.save(user);
    }
}
