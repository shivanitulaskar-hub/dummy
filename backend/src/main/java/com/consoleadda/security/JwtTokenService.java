package com.consoleadda.security;

import com.consoleadda.config.AppProperties;
import com.consoleadda.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues signed (HS256) access tokens. Validation is done by Spring's resource-server support. */
@Service
public class JwtTokenService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, AppProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.security().jwtTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.security().jwtIssuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.roleCode().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
