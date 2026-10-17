package com.ognjen.diplomski.security;

import com.ognjen.diplomski.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record AppUserPrincipal(
        Long id,
        String email,
        String password,
        String firstName,
        String lastName,
        boolean active,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    public static AppUserPrincipal from(AppUser user) {
        String role = user.getRole().getName().toUpperCase(Locale.ROOT);
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(authority));
        user.getRole().getPermissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getName()))
                .forEach(authorities::add);
        return new AppUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                user.isActive(),
                List.copyOf(authorities)
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public boolean hasRole(String role) {
        String normalizedRole = role.toUpperCase(Locale.ROOT);
        String authority = normalizedRole.startsWith("ROLE_")
                ? normalizedRole
                : "ROLE_" + normalizedRole;
        return authorities.stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
