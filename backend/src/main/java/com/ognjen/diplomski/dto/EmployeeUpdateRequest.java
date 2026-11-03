package com.ognjen.diplomski.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EmployeeUpdateRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String email,
        String phone,
        Long managerId,
        String role,
        String password
) {
}
