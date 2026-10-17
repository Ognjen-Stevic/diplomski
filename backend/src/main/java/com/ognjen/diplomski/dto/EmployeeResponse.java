package com.ognjen.diplomski.dto;

public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String role,
        boolean active,
        EmployeeManagerResponse manager
) {
}
