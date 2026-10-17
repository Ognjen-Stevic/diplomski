package com.ognjen.diplomski.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUserPrincipalTests {

    @Test
    void hasRoleAcceptsRoleNamesWithOrWithoutSpringPrefix() {
        AppUserPrincipal principal = new AppUserPrincipal(
                1L,
                "manager@example.com",
                "password-hash",
                "Test",
                "Manager",
                true,
                List.of(
                        new SimpleGrantedAuthority("ROLE_MANAGER"),
                        new SimpleGrantedAuthority("customers.read")
                )
        );

        assertTrue(principal.hasRole("MANAGER"));
        assertTrue(principal.hasRole("ROLE_MANAGER"));
        assertTrue(principal.hasRole("manager"));
        assertFalse(principal.hasRole("SALES_REP"));
        assertFalse(principal.hasRole("customers.read"));
    }
}
