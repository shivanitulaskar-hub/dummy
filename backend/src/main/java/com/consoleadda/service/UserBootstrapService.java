package com.consoleadda.service;

import com.consoleadda.config.AppProperties;
import com.consoleadda.entity.Role;
import com.consoleadda.entity.RoleCode;
import com.consoleadda.entity.User;
import com.consoleadda.repository.RoleRepository;
import com.consoleadda.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the configured bootstrap users on startup if they don't exist yet. Existing users are never
 * modified, so changing a password in the app is not undone by a restart.
 * <p>
 * Development profile: creates the documented dev accounts. Production profile: creates one admin whose
 * password comes from {@code APP_BOOTSTRAP_ADMIN_PASSWORD} and who must change it at first login.
 */
@Slf4j
@Service
public class UserBootstrapService implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserBootstrapService(AppProperties properties, UserRepository userRepository, RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (AppProperties.BootstrapUser candidate : properties.bootstrap().usersOrEmpty()) {
            if (userRepository.existsByUsernameIgnoreCase(candidate.username())) {
                continue;
            }
            if (!StringUtils.hasText(candidate.password())) {
                log.warn("Bootstrap user '{}' skipped: no password configured", candidate.username());
                continue;
            }
            RoleCode roleCode = RoleCode.valueOf(candidate.role());
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Role " + roleCode + " missing; check Flyway seed"));
            User user = new User();
            user.setUsername(candidate.username());
            user.setFullName(candidate.fullName());
            user.setPasswordHash(passwordEncoder.encode(candidate.password()));
            user.setRole(role);
            user.setActive(true);
            user.setMustChangePassword(candidate.mustChangePassword());
            userRepository.save(user);
            log.info("Created bootstrap user '{}' with role {}", candidate.username(), roleCode);
        }
        if (userRepository.count() == 0) {
            throw new IllegalStateException("No users exist. Set APP_BOOTSTRAP_ADMIN_PASSWORD to create the first "
                    + "admin account (see README).");
        }
    }
}
