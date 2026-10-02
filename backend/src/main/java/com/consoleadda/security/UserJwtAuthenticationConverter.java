package com.consoleadda.security;

import com.consoleadda.entity.User;
import com.consoleadda.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Turns a valid JWT into an authenticated principal by re-reading the user from the database.
 * Rejects tokens of deactivated users and tokens issued before the user's last password change.
 */
@Slf4j
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public UserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Object uidClaim = jwt.getClaims().get(JwtTokenService.CLAIM_USER_ID);
        if (!(uidClaim instanceof Number uid)) {
            throw new InvalidBearerTokenException("Token has no user id");
        }
        User user = userRepository.findById(uid.longValue())
                .orElseThrow(() -> new InvalidBearerTokenException("User no longer exists"));
        if (!user.isActive()) {
            log.warn("Rejected token for deactivated user '{}'", user.getUsername());
            throw new InvalidBearerTokenException("User is deactivated");
        }
        Instant issuedAt = jwt.getIssuedAt();
        Instant passwordChangedAt = user.getPasswordChangedAt();
        if (issuedAt != null && passwordChangedAt != null
                && issuedAt.isBefore(passwordChangedAt.truncatedTo(ChronoUnit.SECONDS))) {
            throw new InvalidBearerTokenException("Token was issued before the last password change");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(),
                user.roleCode(), user.isMustChangePassword());
        return new UserAuthenticationToken(principal, jwt);
    }
}
