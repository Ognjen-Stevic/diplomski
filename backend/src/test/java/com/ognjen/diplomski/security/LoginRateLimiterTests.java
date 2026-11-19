package com.ognjen.diplomski.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTests {

    @Test
    void limitsAnIdentityAcrossDifferentAddressesWithoutRevealingAccountExistence() {
        LoginRateLimiter limiter = limiter(100, 2, 100, Instant.parse("2026-01-01T00:00:00Z"));

        limiter.checkAttempt("192.0.2.1", " USER@example.com ");
        limiter.checkAttempt("192.0.2.2", "user@EXAMPLE.com");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> limiter.checkAttempt("192.0.2.3", "user@example.com"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }

    @Test
    void limitsOneAddressAcrossDifferentIdentities() {
        LoginRateLimiter limiter = limiter(2, 100, 100, Instant.parse("2026-01-01T00:00:00Z"));

        limiter.checkAttempt("192.0.2.1", "one@example.com");
        limiter.checkAttempt("192.0.2.1", "two@example.com");

        assertThrows(ResponseStatusException.class,
                () -> limiter.checkAttempt("192.0.2.1", "three@example.com"));
    }

    @Test
    void allowsAttemptsAgainAfterTheTemporaryWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(Duration.ofMinutes(15), 1, 1, 100, clock);
        limiter.checkAttempt("192.0.2.1", "user@example.com");

        clock.advance(Duration.ofMinutes(16));

        assertDoesNotThrow(() -> limiter.checkAttempt("192.0.2.1", "user@example.com"));
    }

    @Test
    void rejectsNewKeysWhenTheConfiguredMemoryBoundIsReached() {
        LoginRateLimiter limiter = limiter(100, 100, 2, Instant.parse("2026-01-01T00:00:00Z"));
        limiter.checkAttempt("192.0.2.1", "one@example.com");

        assertThrows(ResponseStatusException.class,
                () -> limiter.checkAttempt("192.0.2.2", "two@example.com"));
    }

    private static LoginRateLimiter limiter(int ipMaximum, int identityMaximum,
                                            int bucketMaximum, Instant now) {
        return new LoginRateLimiter(Duration.ofMinutes(15), ipMaximum, identityMaximum,
                bucketMaximum, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
