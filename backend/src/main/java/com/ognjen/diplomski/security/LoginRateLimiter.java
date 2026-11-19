package com.ognjen.diplomski.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** A bounded in-memory login limiter that deliberately uses only the servlet remote address. */
@Component
public class LoginRateLimiter {

    private static final String LIMIT_MESSAGE = "Too many login attempts. Try again later.";

    private final Duration window;
    private final int maxAttemptsPerIp;
    private final int maxAttemptsPerIdentity;
    private final int maxBuckets;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    @Autowired
    public LoginRateLimiter(
            @Value("${security.login-rate-limit.window:PT15M}") Duration window,
            @Value("${security.login-rate-limit.max-attempts-per-ip:50}") int maxAttemptsPerIp,
            @Value("${security.login-rate-limit.max-attempts-per-identity:10}") int maxAttemptsPerIdentity,
            @Value("${security.login-rate-limit.max-buckets:10000}") int maxBuckets
    ) {
        this(window, maxAttemptsPerIp, maxAttemptsPerIdentity, maxBuckets, Clock.systemUTC());
    }

    LoginRateLimiter(Duration window, int maxAttemptsPerIp, int maxAttemptsPerIdentity,
                     int maxBuckets, Clock clock) {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Login rate-limit window must be positive");
        }
        if (maxAttemptsPerIp < 1 || maxAttemptsPerIdentity < 1 || maxBuckets < 2) {
            throw new IllegalArgumentException("Login rate-limit values must be positive");
        }
        this.window = window;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
        this.maxAttemptsPerIdentity = maxAttemptsPerIdentity;
        this.maxBuckets = maxBuckets;
        this.clock = clock;
    }

    public synchronized void checkAttempt(String remoteAddress, String identity) {
        Instant now = clock.instant();
        String ipKey = "ip:" + normalizeRemoteAddress(remoteAddress);
        String identityKey = "identity:" + hashIdentity(identity);
        Bucket ipBucket = activeBucket(ipKey, now);
        Bucket identityBucket = activeBucket(identityKey, now);

        if (isLimited(ipBucket, maxAttemptsPerIp) || isLimited(identityBucket, maxAttemptsPerIdentity)) {
            reject();
        }

        int requiredBuckets = (ipBucket == null ? 1 : 0) + (identityBucket == null ? 1 : 0);
        if (buckets.size() + requiredBuckets > maxBuckets) {
            buckets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            if (buckets.size() + requiredBuckets > maxBuckets) {
                reject();
            }
        }

        buckets.put(ipKey, increment(ipBucket, now));
        buckets.put(identityKey, increment(identityBucket, now));
    }

    private Bucket activeBucket(String key, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket != null && !bucket.expiresAt().isAfter(now)) {
            buckets.remove(key);
            return null;
        }
        return bucket;
    }

    private Bucket increment(Bucket bucket, Instant now) {
        return bucket == null
                ? new Bucket(1, now.plus(window))
                : new Bucket(bucket.attempts() + 1, bucket.expiresAt());
    }

    private static boolean isLimited(Bucket bucket, int maximum) {
        return bucket != null && bucket.attempts() >= maximum;
    }

    private static String normalizeRemoteAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
    }

    private static String hashIdentity(String identity) {
        String normalized = identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void reject() {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, LIMIT_MESSAGE);
    }

    private record Bucket(int attempts, Instant expiresAt) {
    }
}
