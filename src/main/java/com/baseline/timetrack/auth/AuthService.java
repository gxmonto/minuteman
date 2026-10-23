package com.baseline.timetrack.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.baseline.timetrack.common.ApiException;
import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties.Jwt jwtProps;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       AppProperties props, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProps = props.jwt();
        this.clock = clock;
    }

    public record IssuedToken(String accessToken, Instant expiresAt, User user) {
    }

    public IssuedToken login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || !user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProps.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProps.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt, user);
    }
}
