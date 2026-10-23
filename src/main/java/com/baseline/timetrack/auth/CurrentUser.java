package com.baseline.timetrack.auth;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** The authenticated user's id; always taken from the token, never from the request. */
    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
