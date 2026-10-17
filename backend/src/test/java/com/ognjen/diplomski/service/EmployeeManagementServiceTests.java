package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.EmployeeActiveRequest;
import com.ognjen.diplomski.dto.EmployeeCreateRequest;
import com.ognjen.diplomski.dto.EmployeeUpdateRequest;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Role;
import com.ognjen.diplomski.exception.EmailAlreadyExistsException;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.RoleRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeManagementServiceTests {

    private AppUserRepository users;
    private RoleRepository roles;
    private CustomerRepository customers;
    private PasswordEncoder passwordEncoder;
    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        roles = mock(RoleRepository.class);
        customers = mock(CustomerRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new EmployeeServiceImpl(users, roles, customers, passwordEncoder);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser employee = invocation.getArgument(0);
            if (employee.getId() == null) employee.setId(100L);
            return employee;
        });
    }

    @Test
    void ceoCreatesManagerWithEncodedPasswordAndNoManager() {
        Role managerRole = role("MANAGER");
        when(roles.findByNameIgnoreCase("MANAGER")).thenReturn(Optional.of(managerRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");

        var response = service.createEmployee(ceo(), new EmployeeCreateRequest(
                "Mila", "Marić", "MILA@example.com", "password123", "0601", "MANAGER", null));

        assertEquals("mila@example.com", response.email());
        assertEquals("MANAGER", response.role());
        assertNull(response.manager());
        verify(passwordEncoder).encode("password123");
        verify(users).saveAndFlush(argThat(employee -> "encoded".equals(employee.getPasswordHash())));
    }

    @Test
    void ceoCreatesSalesRepAssignedToAnActiveManager() {
        AppUser manager = employee(7L, "MANAGER", true);
        when(users.findByIdAndRoleNameIgnoreCase(7L, "MANAGER")).thenReturn(Optional.of(manager));
        when(roles.findByNameIgnoreCase("SALES_REP")).thenReturn(Optional.of(role("SALES_REP")));
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        var response = service.createEmployee(ceo(), new EmployeeCreateRequest(
                "Ana", "Anić", "ana@example.com", "password123", null, "SALES_REP", 7L));

        assertEquals(7L, response.manager().id());
    }

    @Test
    void salesRepCreationRequiresAnActiveManager() {
        when(roles.findByNameIgnoreCase("SALES_REP")).thenReturn(Optional.of(role("SALES_REP")));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.createEmployee(ceo(), new EmployeeCreateRequest(
                        "Ana", "Anić", "ana@example.com", "password123", null, "SALES_REP", null)));
        assertTrue(missing.getMessage().contains("Manager"));

        AppUser inactiveManager = employee(7L, "MANAGER", false);
        when(users.findByIdAndRoleNameIgnoreCase(7L, "MANAGER")).thenReturn(Optional.of(inactiveManager));
        IllegalArgumentException inactive = assertThrows(IllegalArgumentException.class,
                () -> service.createEmployee(ceo(), new EmployeeCreateRequest(
                        "Ana", "Anić", "ana2@example.com", "password123", null, "SALES_REP", 7L)));
        assertTrue(inactive.getMessage().contains("active"));

        when(users.findByIdAndRoleNameIgnoreCase(8L, "MANAGER")).thenReturn(Optional.empty());
        IllegalArgumentException nonManager = assertThrows(IllegalArgumentException.class,
                () -> service.createEmployee(ceo(), new EmployeeCreateRequest(
                        "Ana", "Anić", "ana3@example.com", "password123", null, "SALES_REP", 8L)));
        assertTrue(nonManager.getMessage().contains("does not exist"));
    }

    @Test
    void ceoCanCreateBookkeeperAndDuplicateEmailIsRejected() {
        when(roles.findByNameIgnoreCase("BOOKKEEPER")).thenReturn(Optional.of(role("BOOKKEEPER")));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        var bookkeeper = service.createEmployee(ceo(), new EmployeeCreateRequest(
                "Boba", "Knjigovodja", "bk@example.com", "password123", null, "BOOKKEEPER", null));
        assertEquals("BOOKKEEPER", bookkeeper.role());
        assertNull(bookkeeper.manager());

        when(users.existsByEmailIgnoreCase("used@example.com")).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class, () -> service.createEmployee(ceo(),
                new EmployeeCreateRequest("M", "M", "used@example.com", "password123", null, "MANAGER", null)));
    }

    @Test
    void editChangesAllowedFieldsButPreservesRoleAndPassword() {
        AppUser salesRep = employee(25L, "SALES_REP", true);
        salesRep.setPasswordHash("original-hash");
        AppUser manager = employee(7L, "MANAGER", true);
        when(users.findDetailedById(25L)).thenReturn(Optional.of(salesRep));
        when(users.findByIdAndRoleNameIgnoreCase(7L, "MANAGER")).thenReturn(Optional.of(manager));

        var response = service.updateEmployee(ceo(), 25L,
                new EmployeeUpdateRequest("Novo", "Ime", "new@example.com", "060", 7L, null, null));

        assertEquals("SALES_REP", response.role());
        assertEquals("original-hash", salesRep.getPasswordHash());
        assertEquals(7L, response.manager().id());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void editRequestCannotChangeRoleOrPassword() {
        assertThrows(IllegalArgumentException.class, () -> service.updateEmployee(ceo(), 25L,
                new EmployeeUpdateRequest("Novo", "Ime", "new@example.com", null, 7L,
                        "MANAGER", null)));
        assertThrows(IllegalArgumentException.class, () -> service.updateEmployee(ceo(), 25L,
                new EmployeeUpdateRequest("Novo", "Ime", "new@example.com", null, 7L,
                        null, "new-password")));
        verifyNoInteractions(users, roles, customers, passwordEncoder);
    }

    @Test
    void managerWithAssignedSalesRepsCannotBeDeactivated() {
        AppUser manager = employee(7L, "MANAGER", true);
        when(users.findDetailedById(7L)).thenReturn(Optional.of(manager));
        when(users.existsByManagerIdAndRoleNameIgnoreCase(7L, "SALES_REP")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.setEmployeeActive(ceo(), 7L, new EmployeeActiveRequest(false)));
        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("dodeljeni komercijalisti"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void salesRepWithAssignedCustomersCannotBeDeactivated() {
        AppUser salesRep = employee(25L, "SALES_REP", true);
        when(users.findDetailedById(25L)).thenReturn(Optional.of(salesRep));
        when(customers.existsBySalesRepId(25L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.setEmployeeActive(ceo(), 25L, new EmployeeActiveRequest(false)));
        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("dodeljene kupce"));
    }

    @Test
    void unassignedEmployeeCanBeDeactivatedAndInactiveEmployeeReactivated() {
        AppUser bookkeeper = employee(31L, "BOOKKEEPER", true);
        when(users.findDetailedById(31L)).thenReturn(Optional.of(bookkeeper));
        assertFalse(service.setEmployeeActive(ceo(), 31L, new EmployeeActiveRequest(false)).active());
        assertTrue(service.setEmployeeActive(ceo(), 31L, new EmployeeActiveRequest(true)).active());
    }

    @Test
    void nonCeoCannotUseEmployeeManagementOperations() {
        AppUserPrincipal manager = principal(7L, "ROLE_MANAGER");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.getEmployees(manager)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.createEmployee(manager, null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.updateEmployee(manager, 1L, null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.setEmployeeActive(manager, 1L, null)).getStatusCode().value());
        verifyNoInteractions(users, roles, customers, passwordEncoder);
    }

    private static Role role(String name) {
        Role role = new Role(); role.setName(name); return role;
    }

    private static AppUser employee(Long id, String roleName, boolean active) {
        AppUser employee = new AppUser();
        employee.setId(id); employee.setFirstName("Test"); employee.setLastName("User");
        employee.setEmail("test" + id + "@example.com"); employee.setRole(role(roleName));
        employee.setActive(active); return employee;
    }

    private static AppUserPrincipal ceo() { return principal(1L, "ROLE_CEO"); }

    private static AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(id, "user@example.com", "hash", "Test", "User", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
