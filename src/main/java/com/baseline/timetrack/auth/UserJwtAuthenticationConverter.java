package com.baseline.timetrack.auth;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.repository.UserRepository;

/**
 * Re-reads the user on every request so deactivations and role changes apply immediately,
 * not only when the token expires.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public UserJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user;
        try {
            user = users.findById(Long.valueOf(jwt.getSubject())).orElse(null);
        } catch (NumberFormatException e) {
            user = null;
        }
        if (user == null || !user.isActive()) {
            throw new DisabledException("User is not active");
        }
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getId().toString());
    }
}
