package com.ognjen.diplomski.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EmployeeCreateRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String phone,
        String role,
        Long managerId
) {
}
