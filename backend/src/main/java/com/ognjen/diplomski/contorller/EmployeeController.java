package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.*;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.EmployeeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public List<EmployeeResponse> getEmployees(@AuthenticationPrincipal AppUserPrincipal principal) {
        return employeeService.getEmployees(principal);
    }

    @GetMapping("/manager-options")
    public List<EmployeeManagerResponse> getActiveManagers(@AuthenticationPrincipal AppUserPrincipal principal) {
        return employeeService.getActiveManagers(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse createEmployee(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody EmployeeCreateRequest request
    ) {
        return employeeService.createEmployee(principal, request);
    }

    @PatchMapping("/{employeeId}")
    public EmployeeResponse updateEmployee(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeUpdateRequest request
    ) {
        return employeeService.updateEmployee(principal, employeeId, request);
    }

    @PatchMapping("/{employeeId}/active")
    public EmployeeResponse setEmployeeActive(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeActiveRequest request
    ) {
        return employeeService.setEmployeeActive(principal, employeeId, request);
    }

    @GetMapping("/team-summary")
    public List<TeamMemberSummaryResponse> getManagerTeamSummary(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) Long managerId
    ) {
        return employeeService.getManagerTeamSummary(principal, managerId);
    }

    @GetMapping("/managers")
    public List<ManagerTeamSummaryResponse> getManagersOverview(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return employeeService.getManagersOverview(principal);
    }
}
