package com.ognjen.diplomski.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTests {

    private static final String SECRET =
            "a-test-only-secret-with-at-least-thirty-two-bytes";
    private static final String ISSUER = "test-issuer";
    private static final String AUDIENCE = "test-audience";

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
        JwtService service = service(SECRET);

        String token = service.generateToken(principal);
        var claims = service.parseAndValidate(token);

        assertEquals(principal.email(), claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(AUDIENCE, claims.getAudience());
        assertTrue(service.isValid(claims, principal));
        assertEquals(3600, service.getExpirationSeconds());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService issuer = service(SECRET);
        JwtService verifier = new JwtService(
                "a-different-test-secret-with-at-least-thirty-two-bytes",
                Duration.ofHours(1), ISSUER, AUDIENCE);

        String token = issuer.generateToken(principal);

        assertThrows(JwtException.class, () -> verifier.parseAndValidate(token));
    }

    @Test
    void rejectsMissingIssuerAndAudience() {
        String token = Jwts.builder()
                .setSubject(principal.email())
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> service(SECRET).parseAndValidate(token));
    }

    @Test
    void rejectsMismatchedIssuerOrAudience() {
        String token = new JwtService(SECRET, Duration.ofHours(1), "other-issuer", AUDIENCE)
                .generateToken(principal);
        assertThrows(JwtException.class, () -> service(SECRET).parseAndValidate(token));

        String otherAudience = new JwtService(SECRET, Duration.ofHours(1), ISSUER, "other-audience")
                .generateToken(principal);
        assertThrows(JwtException.class, () -> service(SECRET).parseAndValidate(otherAudience));
    }

    @Test
    void rejectsExpiredToken() {
        String token = Jwts.builder()
                .setSubject(principal.email())
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setIssuedAt(Date.from(Instant.now().minusSeconds(120)))
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(JwtException.class, () -> service(SECRET).parseAndValidate(token));
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("too-short", Duration.ofHours(1), ISSUER, AUDIENCE));
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, Duration.ZERO, ISSUER, AUDIENCE));
    }

    @Test
    void rejectsBlankIssuerAndAudienceConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, Duration.ofHours(1), " ", AUDIENCE));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, Duration.ofHours(1), ISSUER, " "));
    }

    private static JwtService service(String secret) {
        return new JwtService(secret, Duration.ofHours(1), ISSUER, AUDIENCE);
    }
}
