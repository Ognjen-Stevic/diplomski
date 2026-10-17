package com.ognjen.diplomski.dto;

public record ManagerTeamSummaryResponse(
        Long managerId,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean active,
        long teamMemberCount,
        long activeCustomers,
        long currentMonthRequisitions
) {
}
