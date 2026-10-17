package com.ognjen.diplomski.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTests {

    private static final String SECRET =
            "a-test-only-secret-with-at-least-thirty-two-bytes";

    private final AppUserPrincipal principal = new AppUserPrincipal(
            7L,
            "person@example.com",
            "password-hash",
            "Test",
            "Person",
            true,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );

    @Test
    void createsAndValidatesToken() {
        JwtService service = new JwtService(SECRET, Duration.ofHours(1));

        String token = service.generateToken(principal);

        assertEquals(principal.email(), service.extractUsername(token));
        assertTrue(service.isValid(token, principal));
        assertEquals(3600, service.getExpirationSeconds());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService issuer = new JwtService(SECRET, Duration.ofHours(1));
        JwtService verifier = new JwtService(
                "a-different-test-secret-with-at-least-thirty-two-bytes",
                Duration.ofHours(1));

        String token = issuer.generateToken(principal);

        assertThrows(JwtException.class, () -> verifier.extractUsername(token));
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("too-short", Duration.ofHours(1)));
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, Duration.ZERO));
    }
}
