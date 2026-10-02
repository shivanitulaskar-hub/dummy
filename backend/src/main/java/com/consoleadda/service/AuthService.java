package com.consoleadda.service;

import com.consoleadda.dto.auth.ChangePasswordRequest;
import com.consoleadda.dto.auth.LoginRequest;
import com.consoleadda.dto.auth.LoginResponse;
import com.consoleadda.entity.User;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.mapper.UserMapper;
import com.consoleadda.repository.UserRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.JwtTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Login, password change and brute-force protection (temporary lock after repeated failures).
 */
@Slf4j
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final UserMapper userMapper;
    private final Clock clock;
    /** Hash compared against when the user does not exist, so response time doesn't reveal valid usernames. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       UserMapper userMapper, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Failed-attempt counters must be committed even though the call ends in an exception. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String remoteAddress) {
        Instant now = clock.instant();
        Optional<User> found = userRepository.findByUsernameIgnoreCase(request.username().trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            log.warn("Login failed: unknown username '{}' from {}", request.username(), remoteAddress);
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            log.warn("Login blocked: user '{}' is locked until {} (from {})", user.getUsername(), user.getLockedUntil(),
                    remoteAddress);
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes());
            throw new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again in " + minutes + " minute(s).");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(user, now, remoteAddress);
            throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            log.warn("Login failed: user '{}' is deactivated (from {})", user.getUsername(), remoteAddress);
            throw unauthorized("ACCOUNT_DISABLED", "This account has been deactivated");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        log.info("User '{}' logged in from {}", user.getUsername(), remoteAddress);
        return buildLoginResponse(user);
    }

    @Transactional
    public LoginResponse changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> unauthorized("UNAUTHORIZED", "User no longer exists"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Password change failed for '{}': wrong current password", user.getUsername());
            throw BusinessException.badRequest("WRONG_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_REUSED", "New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(clock.instant());
        log.info("User '{}' changed their password", user.getUsername());
        // Tokens issued before this moment are now rejected, so hand back a fresh one.
        return buildLoginResponse(user);
    }

    private void registerFailure(User user, Instant now, String remoteAddress) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plus(LOCK_DURATION));
            user.setFailedLoginAttempts(0);
            log.warn("User '{}' locked for {} after {} failed logins (last from {})", user.getUsername(),
                    LOCK_DURATION, attempts, remoteAddress);
        } else {
            log.warn("Login failed: wrong password for '{}' (attempt {}) from {}", user.getUsername(), attempts,
                    remoteAddress);
        }
    }

    private LoginResponse buildLoginResponse(User user) {
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), userMapper.toInfo(user));
    }

    private static BusinessException unauthorized(String code, String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
