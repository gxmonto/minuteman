package com.baseline.timetrack.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authenticates external systems by the {@code X-Api-Key} header; the principal name is the provider. */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    public static final String ROLE = "INTEGRATION";

    private final Map<String, byte[]> keyHashesByProvider;

    public ApiKeyAuthenticationFilter(Map<String, String> apiKeysByProvider) {
        this.keyHashesByProvider = apiKeysByProvider.entrySet().stream()
                .filter(e -> StringUtils.hasText(e.getValue()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> sha256(e.getValue())));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (StringUtils.hasText(key)) {
            byte[] presented = sha256(key);
            // Compare fixed-length hashes in constant time.
            keyHashesByProvider.entrySet().stream()
                    .filter(e -> MessageDigest.isEqual(e.getValue(), presented))
                    .findFirst()
                    .ifPresent(e -> {
                        var auth = UsernamePasswordAuthenticationToken.authenticated(
                                e.getKey(), null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(auth);
                        SecurityContextHolder.setContext(context);
                    });
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
