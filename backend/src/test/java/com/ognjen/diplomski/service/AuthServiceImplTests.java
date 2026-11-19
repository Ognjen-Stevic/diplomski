package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.LoginRequest;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.security.JwtService;
import com.ognjen.diplomski.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTests {

    @Test
    void loginNormalizesEmailAuthenticatesPasswordAndReturnsJwtResponse() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        JwtService jwtService = mock(JwtService.class);
        Authentication authentication = mock(Authentication.class);
        AppUserPrincipal principal = new AppUserPrincipal(
                7L,
                "user@example.com",
                "encoded-password",
                "Test",
                "User",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_REP"))
        );
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtService.generateToken(principal)).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        AuthServiceImpl service = new AuthServiceImpl(authenticationManager, jwtService);

        var response = service.login(new LoginRequest("  USER@Example.COM ", "plain-password"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationRequest =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authenticationRequest.capture());
        assertEquals("user@example.com", authenticationRequest.getValue().getPrincipal());
        assertEquals("plain-password", authenticationRequest.getValue().getCredentials());
        assertEquals("signed-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertEquals(7L, response.user().id());
        verify(jwtService).generateToken(principal);
    }
}
