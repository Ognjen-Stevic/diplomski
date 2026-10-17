package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.Customer;

public record RequisitionBuyerResponse(
        Long id,
        String objectName,
        String companyName,
        String email
) {
    public static RequisitionBuyerResponse from(Customer customer) {
        return new RequisitionBuyerResponse(
                customer.getId(),
                customer.getObjectName(),
                customer.getCompanyName(),
                customer.getEmail()
        );
    }
}
