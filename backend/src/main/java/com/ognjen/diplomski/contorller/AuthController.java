package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.AuthResponse;
import com.ognjen.diplomski.dto.LoginRequest;
import com.ognjen.diplomski.dto.UserResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.security.LoginRateLimiter;
import com.ognjen.diplomski.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthServiceImpl authService;
    private final LoginRateLimiter loginRateLimiter;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        loginRateLimiter.checkAttempt(servletRequest.getRemoteAddr(), request.email());
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return UserResponse.from(principal);
    }
}
