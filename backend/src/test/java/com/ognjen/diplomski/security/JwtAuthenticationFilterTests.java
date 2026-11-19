package com.ognjen.diplomski.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void parsesAndCryptographicallyValidatesTokenExactlyOnce() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed-token");
        var response = new MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();
        Claims claims = Jwts.claims().setSubject("person@example.com");
        AppUserPrincipal principal = principal();
        when(jwtService.parseAndValidate("signed-token")).thenReturn(claims);
        when(users.loadUserByUsername("person@example.com")).thenReturn(principal);
        when(jwtService.isValid(claims, principal)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(jwtService, times(1)).parseAndValidate("signed-token");
        assertEquals(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void invalidTokenDoesNotLoadAUserOrAuthenticate() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtService.parseAndValidate("invalid-token")).thenThrow(new JwtException("invalid"));

        filter.doFilter(request, new MockHttpServletResponse(), new org.springframework.mock.web.MockFilterChain());

        verify(jwtService, times(1)).parseAndValidate("invalid-token");
        verify(users, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private static AppUserPrincipal principal() {
        return new AppUserPrincipal(7L, "person@example.com", "hash", "Test", "Person", true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
