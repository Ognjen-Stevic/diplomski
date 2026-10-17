package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.security.AppUserPrincipal;

import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String role,
        Set<String> permissions
) {
    public static UserResponse from(AppUserPrincipal principal) {
        String role = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
        return new UserResponse(
                principal.id(),
                principal.email(),
                principal.firstName(),
                principal.lastName(),
                role,
                principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> !authority.startsWith("ROLE_"))
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
}
