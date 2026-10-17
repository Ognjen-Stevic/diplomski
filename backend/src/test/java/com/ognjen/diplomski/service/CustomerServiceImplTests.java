package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.CustomerRequest;
import com.ognjen.diplomski.dto.CustomerResponse;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerServiceImplTests {

    @Test
    void customerCardCapabilitiesAreComputedFromAuthenticatedRole() {
        Customer customer = customerOwnedBy(20L, 1L);

        CustomerResponse salesRep = CustomerResponse.from(
                customer, principalWithRole("ROLE_SALES_REP"));
        assertEquals(true, salesRep.actions().canCreateRequisition());
        assertEquals(false, salesRep.actions().canViewRequisitionHistory());
        assertEquals(false, salesRep.actions().canChangeSalesRep());
        assertEquals(null, salesRep.salesRep());

        CustomerResponse manager = CustomerResponse.from(
                customer, principalWithRole("ROLE_MANAGER"));
        assertEquals(false, manager.actions().canCreateRequisition());
        assertEquals(true, manager.actions().canViewRequisitionHistory());
        assertEquals(true, manager.actions().canChangeSalesRep());
        assertEquals(20L, manager.salesRep().id());

        CustomerResponse ceo = CustomerResponse.from(
                customer, principalWithRole("ROLE_CEO"));
        assertEquals(false, ceo.actions().canCreateRequisition());
        assertEquals(true, ceo.actions().canViewRequisitionHistory());
        assertEquals(false, ceo.actions().canChangeSalesRep());
        assertEquals(20L, ceo.salesRep().id());
    }

    @Test
    void customerListIsScopedByRole() {
        assertListScope("ROLE_CEO", true, false, false);
        assertListScope("ROLE_MANAGER", false, true, false);
        assertListScope("ROLE_SALES_REP", false, false, true);
        assertListScope("ROLE_USER", false, false, false);
    }

    @Test
    void managerCanOpenCustomerOwnedByAssignedSalesRep() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));
        Customer customer = customerOwnedBy(20L, 1L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        assertEquals(100L, service.findById(principalWithRole("ROLE_MANAGER"), 100L).id());
    }

    @Test
    void managerCannotOpenCustomerOwnedByAnotherManagersSalesRep() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customerOwnedBy(20L, 2L)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.findById(principalWithRole("ROLE_MANAGER"), 100L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void managerCannotCreateCustomer() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, userRepository);
        AppUserPrincipal manager = principalWithRole("ROLE_MANAGER");
        CustomerRequest request = new CustomerRequest(
                "Object", "Company", "123456789", null, null, "Address", null, null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(manager, request)
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(customerRepository, userRepository);
    }

    @Test
    void managerCannotUpdateCustomer() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));
        CustomerRequest request = new CustomerRequest(
                "Object", "Company", "123456789", null, null, "Address", null, null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(principalWithRole("ROLE_MANAGER"), 1L, request)
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(customerRepository);
    }

    @Test
    void managerCanChangeStatusForCustomerFromOwnTeam() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));
        Customer customer = customerOwnedBy(20L, 1L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        CustomerResponse response = service.updateStatus(
                principalWithRole("ROLE_MANAGER"),
                100L,
                CustomerStatus.DEACTIVATED
        );

        assertEquals(CustomerStatus.DEACTIVATED, response.status());
        verify(customerRepository).saveAndFlush(customer);
    }

    @Test
    void managerCannotChangeStatusForAnotherManagersCustomer() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customerOwnedBy(20L, 2L)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStatus(
                        principalWithRole("ROLE_MANAGER"),
                        100L,
                        CustomerStatus.APPROVED
                )
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void nonManagerCannotChangeCustomerStatus() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStatus(
                        principalWithRole("ROLE_CEO"),
                        100L,
                        CustomerStatus.APPROVED
                )
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(customerRepository);
    }

    @Test
    void managerCanAssignCustomerToAnotherSalesRepresentativeFromOwnTeam() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, userRepository);
        Customer customer = customerOwnedBy(20L, 1L);
        AppUser newSalesRepresentative = salesRepresentativeOwnedBy(21L, 1L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndManagerIdAndActiveTrueAndRoleNameIgnoreCase(
                21L, 1L, "SALES_REP"
        )).thenReturn(Optional.of(newSalesRepresentative));
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        CustomerResponse response = service.updateSalesRepresentative(
                principalWithRole("ROLE_MANAGER"),
                100L,
                21L
        );

        assertEquals(21L, response.salesRep().id());
        verify(customerRepository).saveAndFlush(customer);
    }

    @Test
    void managerCannotAssignCustomerToSalesRepresentativeOutsideOwnTeam() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, userRepository);
        Customer customer = customerOwnedBy(20L, 1L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndManagerIdAndActiveTrueAndRoleNameIgnoreCase(
                30L, 1L, "SALES_REP"
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateSalesRepresentative(
                        principalWithRole("ROLE_MANAGER"),
                        100L,
                        30L
                )
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    private static AppUserPrincipal principalWithRole(String role) {
        return new AppUserPrincipal(
                1L,
                "manager@example.com",
                "password-hash",
                "Test",
                "Manager",
                true,
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    private static void assertListScope(
            String role,
            boolean allCustomers,
            boolean managedSalesReps,
            boolean ownCustomers
    ) {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerServiceImpl service = new CustomerServiceImpl(
                customerRepository, mock(AppUserRepository.class));
        when(customerRepository.search(
                any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyString(), anyBoolean(), any(Pageable.class)
        )).thenReturn(Page.empty());

        service.findAll(principalWithRole(role), null, 0, 50);

        Set<CustomerStatus> visibleStatuses = switch (role) {
            case "ROLE_CEO", "ROLE_MANAGER" -> Set.of(
                    CustomerStatus.DRAFT,
                    CustomerStatus.APPROVED,
                    CustomerStatus.DEACTIVATED
            );
            case "ROLE_SALES_REP" -> Set.of(
                    CustomerStatus.DRAFT,
                    CustomerStatus.APPROVED
            );
            default -> Set.of();
        };

        verify(customerRepository).search(
                eq(visibleStatuses),
                eq(1L),
                eq(allCustomers),
                eq(managedSalesReps),
                eq(ownCustomers),
                eq(""),
                eq(false),
                any(Pageable.class)
        );
    }

    private static Customer customerOwnedBy(Long salesRepId, Long managerId) {
        AppUser salesRep = salesRepresentativeOwnedBy(salesRepId, managerId);

        Customer customer = new Customer();
        customer.setId(100L);
        customer.setSalesRep(salesRep);
        customer.setStatus(CustomerStatus.APPROVED);
        return customer;
    }

    private static AppUser salesRepresentativeOwnedBy(Long salesRepId, Long managerId) {
        AppUser manager = new AppUser();
        manager.setId(managerId);

        AppUser salesRep = new AppUser();
        salesRep.setId(salesRepId);
        salesRep.setManager(manager);
        return salesRep;
    }

}
