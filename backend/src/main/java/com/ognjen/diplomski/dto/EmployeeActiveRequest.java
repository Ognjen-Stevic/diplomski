package com.ognjen.diplomski.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EmployeeActiveRequest(boolean active) {
}
