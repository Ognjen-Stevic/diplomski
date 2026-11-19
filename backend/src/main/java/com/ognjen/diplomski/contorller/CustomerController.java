package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.CustomerPageResponse;
import com.ognjen.diplomski.dto.CustomerRequest;
import com.ognjen.diplomski.dto.CustomerResponse;
import com.ognjen.diplomski.dto.CustomerStatusUpdateRequest;
import com.ognjen.diplomski.dto.CustomerSalesRepresentativeUpdateRequest;
import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public CustomerPageResponse findAll(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return customerService.findAll(principal, search, safePage, safeSize);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse findById(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long customerId
    ) {
        return customerService.findById(principal, customerId);
    }

    @GetMapping("/team-sales-representatives")
    public List<SalesRepresentativeOptionResponse> findTeamSalesRepresentatives(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return customerService.findTeamSalesRepresentatives(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CustomerRequest request
    ) {
        return customerService.create(principal, request);
    }

    @PutMapping("/{customerId}")
    public CustomerResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerRequest request
    ) {
        return customerService.update(principal, customerId, request);
    }

    @PatchMapping("/{customerId}/status")
    public CustomerResponse updateStatus(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerStatusUpdateRequest request
    ) {
        return customerService.updateStatus(principal, customerId, request.status());
    }

    @PatchMapping("/{customerId}/sales-representative")
    public CustomerResponse updateSalesRepresentative(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerSalesRepresentativeUpdateRequest request
    ) {
        return customerService.updateSalesRepresentative(
                principal,
                customerId,
                request.salesRepId()
        );
    }

}
