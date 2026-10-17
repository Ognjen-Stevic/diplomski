package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.Customer;

public record CustomerSummaryResponse(
        Long id,
        String objectName,
        String companyName
) {
    public static CustomerSummaryResponse from(Customer customer) {
        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getObjectName(),
                customer.getCompanyName()
        );
    }
}
