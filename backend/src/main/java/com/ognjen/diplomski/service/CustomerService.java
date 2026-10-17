package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.CustomerPageResponse;
import com.ognjen.diplomski.dto.CustomerRequest;
import com.ognjen.diplomski.dto.CustomerResponse;
import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.security.AppUserPrincipal;

import java.util.List;

public interface CustomerService {

    CustomerPageResponse findAll(AppUserPrincipal principal, String search, int page, int size);

    CustomerResponse findById(AppUserPrincipal principal, Long customerId);

    CustomerResponse create(AppUserPrincipal principal, CustomerRequest request);

    CustomerResponse update(AppUserPrincipal principal, Long customerId, CustomerRequest request);

    CustomerResponse updateStatus(AppUserPrincipal principal, Long customerId, CustomerStatus status);

    List<SalesRepresentativeOptionResponse> findTeamSalesRepresentatives(AppUserPrincipal principal);

    CustomerResponse updateSalesRepresentative(
            AppUserPrincipal principal,
            Long customerId,
            Long salesRepId
    );

}
