package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.*;
import com.ognjen.diplomski.security.AppUserPrincipal;

import java.util.List;

public interface EmployeeService {

    List<TeamMemberSummaryResponse> getManagerTeamSummary(AppUserPrincipal principal);

    List<TeamMemberSummaryResponse> getManagerTeamSummary(
            AppUserPrincipal principal,
            Long managerId
    );

    List<ManagerTeamSummaryResponse> getManagersOverview(AppUserPrincipal principal);

    List<EmployeeResponse> getEmployees(AppUserPrincipal principal);

    List<EmployeeManagerResponse> getActiveManagers(AppUserPrincipal principal);

    EmployeeResponse createEmployee(AppUserPrincipal principal, EmployeeCreateRequest request);

    EmployeeResponse updateEmployee(AppUserPrincipal principal, Long employeeId, EmployeeUpdateRequest request);

    EmployeeResponse setEmployeeActive(AppUserPrincipal principal, Long employeeId, EmployeeActiveRequest request);
}
