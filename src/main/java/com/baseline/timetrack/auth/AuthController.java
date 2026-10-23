package com.baseline.timetrack.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.entity.Role;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.repository.ExternalAccountRepository;
import com.baseline.timetrack.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;
    private final ExternalAccountRepository externalAccounts;

    public AuthController(AuthService authService, UserRepository users, ExternalAccountRepository externalAccounts) {
        this.authService = authService;
        this.users = users;
        this.externalAccounts = externalAccounts;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    }

    public record UserResponse(Long id, String email, String fullName, Role role) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
        }
    }

    public record LinkedAccount(String provider, String externalUserId) {
    }

    public record MeResponse(UserResponse user, List<LinkedAccount> linkedAccounts) {
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.IssuedToken issued = authService.login(request.email(), request.password());
        return new LoginResponse(issued.accessToken(), "Bearer", issued.expiresAt(), UserResponse.of(issued.user()));
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        User user = users.findById(userId).orElseThrow();
        List<LinkedAccount> linked = externalAccounts.findByUserId(userId).stream()
                .map(a -> new LinkedAccount(a.getProvider(), a.getExternalUserId()))
                .toList();
        return new MeResponse(UserResponse.of(user), linked);
    }
}
