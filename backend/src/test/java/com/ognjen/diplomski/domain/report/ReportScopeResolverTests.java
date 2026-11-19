package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportScopeResolverTests {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ReportScopeResolver resolver = new ReportScopeResolver(users);

    @Test
    void ceoWithoutSelectionsGetsOrganizationScope() {
        ReportScopeResolver.ReportScope scope = resolver.resolve(principal(1L, "CEO"), null, null);

        assertTrue(scope.organizationReport());
        assertFalse(scope.teamReport());
        assertEquals(null, scope.salesRepId());
    }

    @Test
    void managerWithoutSalesRepresentativeGetsOwnTeamScope() {
        ReportScopeResolver.ReportScope scope = resolver.resolve(principal(8L, "MANAGER"), null, null);

        assertEquals(8L, scope.managerId());
        assertTrue(scope.teamReport());
        assertTrue(scope.managerStimulation());
    }

    @Test
    void managerCanResolveOnlySalesRepresentativeFromOwnTeam() {
        AppUser salesRep = new AppUser();
        salesRep.setId(25L);
        salesRep.setFirstName("Ana");
        salesRep.setLastName("Anic");
        when(users.findByIdAndManagerIdAndRoleNameIgnoreCase(25L, 8L, "SALES_REP"))
                .thenReturn(Optional.of(salesRep));

        ReportScopeResolver.ReportScope scope = resolver.resolve(principal(8L, "MANAGER"), 25L, null);

        assertEquals(25L, scope.salesRepId());
        assertEquals("Ana", scope.salesRep().firstName());
        assertFalse(scope.teamReport());
        assertTrue(scope.managerStimulation());
    }

    @Test
    void salesRepresentativeCannotViewAnotherRepresentativesReport() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolve(principal(4L, "SALES_REP"), 5L, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("A sales representative can only view their own report", error.getReason());
    }

    @Test
    void ceoManagerSelectionMustReferenceAnExistingManager() {
        when(users.findByIdAndRoleNameIgnoreCase(99L, "MANAGER")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolveManagerId(principal(1L, "CEO"), 99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("Manager not found", error.getReason());
    }

    private AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(
                id,
                role.toLowerCase() + "@example.com",
                "password-hash",
                "Test",
                role,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
