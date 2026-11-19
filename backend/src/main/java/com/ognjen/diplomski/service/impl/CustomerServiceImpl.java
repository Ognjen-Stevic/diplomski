package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.CustomerPageResponse;
import com.ognjen.diplomski.dto.CustomerRequest;
import com.ognjen.diplomski.dto.CustomerResponse;
import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.ognjen.diplomski.security.CustomerAccessPolicy.*;
import java.util.Locale;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerPageResponse findAll(
            AppUserPrincipal principal,
            String search,
            int page,
            int size
    ) {
        CustomerAccessScope accessScope = customerAccessScope(principal);
        String normalizedSearch = normalizeSearch(search);
        boolean hasSearch = !normalizedSearch.isEmpty();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));

        return CustomerPageResponse.from(
                customerRepository
                        .search(
                                visibleStatuses(principal),
                                principal.id(),
                                accessScope.allCustomers(),
                                accessScope.managedSalesReps(),
                                accessScope.ownCustomers(),
                                normalizedSearch,
                                hasSearch,
                                pageable
                        )
                        .map(customer -> CustomerResponse.from(customer, principal))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findById(AppUserPrincipal principal, Long customerId) {
        return CustomerResponse.from(findAccessibleCustomer(principal, customerId), principal);
    }

    @Override
    @Transactional
    public CustomerResponse create(AppUserPrincipal principal, CustomerRequest request) {
        if (!principal.hasRole("SALES_REP")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only sales representatives can create customers"
            );
        }

        Customer customer = new Customer();
        customer.setSalesRep(userRepository.getReferenceById(principal.id()));
        customer.setStatus(CustomerStatus.DRAFT);
        applyRequest(customer, request);
        return CustomerResponse.from(customerRepository.saveAndFlush(customer), principal);
    }

    @Override
    @Transactional
    public CustomerResponse update(
            AppUserPrincipal principal,
            Long customerId,
            CustomerRequest request
    ) {
        requireSalesRep(principal, "update customers");
        Customer customer = findAccessibleCustomer(principal, customerId);
        applyRequest(customer, request);
        return CustomerResponse.from(customerRepository.saveAndFlush(customer), principal);
    }

    @Override
    @Transactional
    public CustomerResponse updateStatus(
            AppUserPrincipal principal,
            Long customerId,
            CustomerStatus status
    ) {
        requireManager(principal);
        Customer customer = findManagerTeamCustomer(principal, customerId);
        customer.setStatus(status);
        return CustomerResponse.from(customerRepository.saveAndFlush(customer), principal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesRepresentativeOptionResponse> findTeamSalesRepresentatives(
            AppUserPrincipal principal
    ) {
        requireManager(principal);
        return userRepository
                .findAllByManagerIdAndActiveTrueAndRoleNameIgnoreCaseOrderByFirstNameAscLastNameAsc(
                        principal.id(),
                        "SALES_REP"
                )
                .stream()
                .map(SalesRepresentativeOptionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CustomerResponse updateSalesRepresentative(
            AppUserPrincipal principal,
            Long customerId,
            Long salesRepId
    ) {
        requireManager(principal);
        Customer customer = findManagerTeamCustomer(principal, customerId);
        var salesRepresentative = userRepository
                .findByIdAndManagerIdAndActiveTrueAndRoleNameIgnoreCase(
                        salesRepId,
                        principal.id(),
                        "SALES_REP"
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales representative not found"
                ));
        customer.setSalesRep(salesRepresentative);
        return CustomerResponse.from(customerRepository.saveAndFlush(customer), principal);
    }

    private Customer findAccessibleCustomer(AppUserPrincipal principal, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .filter(item -> visibleStatuses(principal).contains(item.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        if (!canAccessCustomer(principal, customer)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return customer;
    }

    private Customer findManagerTeamCustomer(AppUserPrincipal principal, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (customer.getSalesRep().getManager() == null
                || !customer.getSalesRep().getManager().getId().equals(principal.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return customer;
    }

    private static void requireSalesRep(AppUserPrincipal principal, String action) {
        if (!principal.hasRole("SALES_REP")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only sales representatives can " + action
            );
        }
    }

    private static void requireManager(AppUserPrincipal principal) {
        if (!principal.hasRole("MANAGER")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only managers can change customer status"
            );
        }
    }

    private static void applyRequest(Customer customer, CustomerRequest request) {
        customer.setObjectName(request.objectName().trim());
        customer.setCompanyName(request.companyName().trim());
        customer.setPib(request.pib().trim());
        customer.setPhone(blankToNull(request.phone()));
        customer.setEmail(blankToNull(request.email()));
        customer.setAddress(request.address().trim());
        customer.setGoogleMapsUrl(blankToNull(request.googleMapsUrl()));
        customer.setNote(blankToNull(request.note()));
    }

    private static String normalizeSearch(String search) {
        return search == null || search.isBlank()
                ? ""
                : search.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
