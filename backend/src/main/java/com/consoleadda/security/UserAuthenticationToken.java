package com.consoleadda.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;
    private final transient Jwt jwt;

    public UserAuthenticationToken(AuthenticatedUser user, Jwt jwt) {
        super(List.of(new SimpleGrantedAuthority(user.role().authority())));
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.username();
    }
}
