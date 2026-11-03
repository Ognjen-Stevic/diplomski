package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.exception.InvalidRequestException;

import com.ognjen.diplomski.exception.EmailAlreadyExistsException;
import com.ognjen.diplomski.dto.AuthResponse;
import com.ognjen.diplomski.dto.LoginRequest;
import com.ognjen.diplomski.dto.RegisterRequest;
import com.ognjen.diplomski.dto.UserResponse;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Role;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.RoleRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.security.JwtService;
import com.ognjen.diplomski.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${security.auth.default-role:USER}")
    private String defaultRole;

    public AuthResponse login(LoginRequest request) {
        requireLoginRequest(request);
        String email = normalizeEmail(request.email());
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        return createResponse((AppUserPrincipal) authentication.getPrincipal());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateRegistration(request);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException();
        }

        Role role = roleRepository.findByNameIgnoreCase(defaultRole)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role '" + defaultRole + "' is not present in the database"));

        AppUser user = new AppUser();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(blankToNull(request.phone()));
        user.setRole(role);
        user.setActive(true);

        AppUser saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
        return createResponse(AppUserPrincipal.from(saved));
    }

    private AuthResponse createResponse(AppUserPrincipal principal) {
        return new AuthResponse(
                jwtService.generateToken(principal),
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(principal)
        );
    }

    private static void requireLoginRequest(LoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            throw new InvalidRequestException("Email and password are required");
        }
    }

    private static void validateRegistration(RegisterRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Request body is required");
        }
        requireLength(request.firstName(), "First name", 1, 50);
        requireLength(request.lastName(), "Last name", 1, 50);
        if (isBlank(request.email()) || request.email().length() > 255
                || !EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            throw new InvalidRequestException("A valid email is required");
        }
        requireLength(request.password(), "Password", 8, Integer.MAX_VALUE);
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must contain at most 72 UTF-8 bytes");
        }
        if (request.phone() != null && request.phone().trim().length() > 50) {
            throw new InvalidRequestException("Phone must contain at most 50 characters");
        }
    }

    private static void requireLength(String value, String field, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        if (length < min || length > max) {
            throw new InvalidRequestException(
                    field + " must contain between " + min + " and " + max + " characters");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
