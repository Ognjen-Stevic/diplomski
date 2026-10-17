package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.security.AppUserPrincipal;

import java.time.OffsetDateTime;

public record CustomerResponse(
        Long id,
        SalesRepresentativeSummary salesRep,
        CustomerActions actions,
        String objectName,
        String companyName,
        String pib,
        String phone,
        String email,
        String address,
        String googleMapsUrl,
        String note,
        CustomerStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CustomerResponse from(Customer customer, AppUserPrincipal principal) {
        boolean salesRepresentative = principal.hasRole("SALES_REP");
        boolean manager = principal.hasRole("MANAGER");
        boolean ceo = principal.hasRole("CEO");

        return new CustomerResponse(
                customer.getId(),
                manager || ceo ? SalesRepresentativeSummary.from(customer) : null,
                new CustomerActions(
                        salesRepresentative && customer.getStatus() == CustomerStatus.APPROVED,
                        manager || ceo,
                        manager
                ),
                customer.getObjectName(),
                customer.getCompanyName(),
                customer.getPib(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getGoogleMapsUrl(),
                customer.getNote(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    public record SalesRepresentativeSummary(
            Long id,
            String firstName,
            String lastName
    ) {
        private static SalesRepresentativeSummary from(Customer customer) {
            return new SalesRepresentativeSummary(
                    customer.getSalesRep().getId(),
                    customer.getSalesRep().getFirstName(),
                    customer.getSalesRep().getLastName()
            );
        }
    }

    public record CustomerActions(
            boolean canCreateRequisition,
            boolean canViewRequisitionHistory,
            boolean canChangeSalesRep
    ) {}
}
