package com.ognjen.diplomski.dto;

public record TeamMemberSummaryResponse(
        Long salesRepId,
        String firstName,
        String lastName,
        String email,
        String phone,
        long activeCustomers,
        long currentMonthRequisitions,
        boolean active
) {
}
