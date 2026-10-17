package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.AppUser;

public record SalesRepresentativeOptionResponse(
        Long id,
        String firstName,
        String lastName
) {
    public static SalesRepresentativeOptionResponse from(AppUser salesRepresentative) {
        return new SalesRepresentativeOptionResponse(
                salesRepresentative.getId(),
                salesRepresentative.getFirstName(),
                salesRepresentative.getLastName()
        );
    }
}
