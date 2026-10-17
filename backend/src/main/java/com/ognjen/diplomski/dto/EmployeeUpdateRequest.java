package com.ognjen.diplomski.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EmployeeUpdateRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        Long managerId,
        String role,
        String password
) {
}
