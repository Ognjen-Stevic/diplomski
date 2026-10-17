package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.RoleRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmployeeServiceImplTests {

    @Test
    void managerReceivesAggregatedTeamMemberSummaries() {
        AppUserRepository repository = mock(AppUserRepository.class);
        AppUserRepository.TeamMemberSummaryProjection member = mock(
                AppUserRepository.TeamMemberSummaryProjection.class
        );
        when(member.getSalesRepId()).thenReturn(25L);
        when(member.getFirstName()).thenReturn("Ana");
        when(member.getLastName()).thenReturn("Anić");
        when(member.getEmail()).thenReturn("ana@example.com");
        when(member.getPhone()).thenReturn("060123456");
        when(member.getActive()).thenReturn(true);
        when(member.getActiveCustomers()).thenReturn(12L);
        when(member.getCurrentMonthRequisitions()).thenReturn(4L);
        when(repository.findTeamMemberSummaries(eq(7L), any(), any())).thenReturn(List.of(member));

        var response = service(repository).getManagerTeamSummary(managerPrincipal());

        assertEquals(1, response.size());
        assertEquals(25L, response.getFirst().salesRepId());
        assertEquals(12L, response.getFirst().activeCustomers());
        assertEquals(4L, response.getFirst().currentMonthRequisitions());
        verify(repository).findTeamMemberSummaries(eq(7L), any(), any());
    }

    @Test
    void nonManagerCannotReadTeamSummary() {
        AppUserRepository repository = mock(AppUserRepository.class);
        EmployeeServiceImpl service = service(repository);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getManagerTeamSummary(principal(25L, "ROLE_SALES_REP"))
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void ceoUsesTheSameTeamSummaryQueryForAValidatedManager() {
        AppUserRepository repository = mock(AppUserRepository.class);
        AppUser manager = new AppUser();
        manager.setId(7L);
        when(repository.findByIdAndRoleNameIgnoreCase(7L, "MANAGER"))
                .thenReturn(Optional.of(manager));
        when(repository.findTeamMemberSummaries(eq(7L), any(), any())).thenReturn(List.of());

        var response = service(repository)
                .getManagerTeamSummary(principal(1L, "ROLE_CEO"), 7L);

        assertEquals(0, response.size());
        verify(repository).findTeamMemberSummaries(eq(7L), any(), any());
    }

    @Test
    void managerCannotSelectAnotherManagerTeam() {
        AppUserRepository repository = mock(AppUserRepository.class);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(repository)
                        .getManagerTeamSummary(managerPrincipal(), 99L)
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    private static AppUserPrincipal managerPrincipal() {
        return principal(7L, "ROLE_MANAGER");
    }

    private static EmployeeServiceImpl service(AppUserRepository repository) {
        return new EmployeeServiceImpl(
                repository,
                mock(RoleRepository.class),
                mock(CustomerRepository.class),
                mock(PasswordEncoder.class)
        );
    }

    private static AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(
                id,
                "user@example.com",
                "password-hash",
                "Test",
                "User",
                true,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
