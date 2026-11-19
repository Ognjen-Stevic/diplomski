package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.AuthResponse;
import com.ognjen.diplomski.dto.LoginRequest;
import com.ognjen.diplomski.dto.UserResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        return createResponse((AppUserPrincipal) authentication.getPrincipal());
    }

    private AuthResponse createResponse(AppUserPrincipal principal) {
        return new AuthResponse(
                jwtService.generateToken(principal),
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(principal)
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
