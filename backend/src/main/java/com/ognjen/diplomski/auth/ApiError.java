package com.ognjen.diplomski.auth;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Shared MVC and security error contract; excludes rejected values and exception details. */
public record ApiError(Instant timestamp, int status, String error, String code,
                       String message, String path, Map<String, String> fieldErrors) {
    public static ApiError of(HttpStatus status, String code, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code,
                message, path, Map.of());
    }
}
