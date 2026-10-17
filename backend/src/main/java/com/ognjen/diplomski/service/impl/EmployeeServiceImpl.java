package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.*;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Role;
import com.ognjen.diplomski.exception.EmailAlreadyExistsException;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.RoleRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final String SALES_REP = "SALES_REP";
    private static final String MANAGER = "MANAGER";
    private static final String BOOKKEEPER = "BOOKKEEPER";
    private static final Set<String> MANAGED_ROLES = Set.of(SALES_REP, MANAGER, BOOKKEEPER);
    private static final Set<String> CREATABLE_ROLES = Set.of(SALES_REP, MANAGER, BOOKKEEPER);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberSummaryResponse> getManagerTeamSummary(AppUserPrincipal principal) {
        return getManagerTeamSummary(principal, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberSummaryResponse> getManagerTeamSummary(
            AppUserPrincipal principal,
            Long requestedManagerId
    ) {
        Long managerId = resolveManagerId(principal, requestedManagerId);

        DateRange range = currentMonthRange();

        return appUserRepository.findTeamMemberSummaries(managerId, range.start(), range.end())
                .stream()
                .map(member -> new TeamMemberSummaryResponse(
                        member.getSalesRepId(),
                        member.getFirstName(),
                        member.getLastName(),
                        member.getEmail(),
                        member.getPhone(),
                        member.getActiveCustomers(),
                        member.getCurrentMonthRequisitions(),
                        member.getActive()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerTeamSummaryResponse> getManagersOverview(AppUserPrincipal principal) {
        requireCeo(principal);

        DateRange range = currentMonthRange();
        return appUserRepository.findManagerTeamSummaries(range.start(), range.end()).stream()
                .map(manager -> new ManagerTeamSummaryResponse(
                        manager.getManagerId(),
                        manager.getFirstName(),
                        manager.getLastName(),
                        manager.getEmail(),
                        manager.getPhone(),
                        manager.getActive(),
                        manager.getTeamMemberCount(),
                        manager.getActiveCustomers(),
                        manager.getCurrentMonthRequisitions()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getEmployees(AppUserPrincipal principal) {
        requireCeo(principal);
        return appUserRepository.findManagedEmployees(MANAGED_ROLES).stream()
                .map(EmployeeServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeManagerResponse> getActiveManagers(AppUserPrincipal principal) {
        requireCeo(principal);
        return appUserRepository
                .findAllByActiveTrueAndRoleNameIgnoreCaseOrderByFirstNameAscLastNameAsc(MANAGER)
                .stream()
                .map(manager -> new EmployeeManagerResponse(
                        manager.getId(), manager.getFirstName(), manager.getLastName()))
                .toList();
    }

    @Override
    @Transactional
    public EmployeeResponse createEmployee(AppUserPrincipal principal, EmployeeCreateRequest request) {
        requireCeo(principal);
        validateCreateRequest(request);
        String roleName = normalizeRole(request.role());
        if (!CREATABLE_ROLES.contains(roleName)) {
            throw new IllegalArgumentException("Only SALES_REP, MANAGER and BOOKKEEPER employees can be created");
        }

        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException();
        }

        Role role = roleRepository.findByNameIgnoreCase(roleName)
                .orElseThrow(() -> new IllegalStateException("Role '" + roleName + "' is not present in the database"));
        AppUser employee = new AppUser();
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setEmail(email);
        employee.setPasswordHash(passwordEncoder.encode(request.password()));
        employee.setPhone(blankToNull(request.phone()));
        employee.setRole(role);
        employee.setManager(resolveManager(roleName, request.managerId()));
        employee.setActive(true);

        return toResponse(save(employee));
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(
            AppUserPrincipal principal,
            Long employeeId,
            EmployeeUpdateRequest request
    ) {
        requireCeo(principal);
        validateUpdateRequest(request);
        AppUser employee = findManagedEmployee(employeeId);
        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmailIgnoreCaseAndIdNot(email, employeeId)) {
            throw new EmailAlreadyExistsException();
        }

        String roleName = employee.getRole().getName().toUpperCase(Locale.ROOT);
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setEmail(email);
        employee.setPhone(blankToNull(request.phone()));
        employee.setManager(resolveManager(roleName, request.managerId()));
        return toResponse(save(employee));
    }

    @Override
    @Transactional
    public EmployeeResponse setEmployeeActive(
            AppUserPrincipal principal,
            Long employeeId,
            EmployeeActiveRequest request
    ) {
        requireCeo(principal);
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        AppUser employee = findManagedEmployee(employeeId);
        if (!request.active() && employee.isActive()) {
            String roleName = employee.getRole().getName().toUpperCase(Locale.ROOT);
            if (MANAGER.equals(roleName)
                    && appUserRepository.existsByManagerIdAndRoleNameIgnoreCase(employeeId, SALES_REP)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Menadžer ne može biti deaktiviran dok su mu dodeljeni komercijalisti. Prvo ih prerasporedite."
                );
            }
            if (SALES_REP.equals(roleName) && customerRepository.existsBySalesRepId(employeeId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Komercijalista ne može biti deaktiviran dok ima dodeljene kupce. Prvo prerasporedite kupce."
                );
            }
        }
        employee.setActive(request.active());
        return toResponse(save(employee));
    }

    private AppUser findManagedEmployee(Long employeeId) {
        AppUser employee = appUserRepository.findDetailedById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!MANAGED_ROLES.contains(employee.getRole().getName().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        return employee;
    }

    private AppUser resolveManager(String roleName, Long managerId) {
        if (SALES_REP.equals(roleName)) {
            if (managerId == null) {
                throw new IllegalArgumentException("Manager is required for SALES_REP");
            }
            AppUser manager = appUserRepository.findByIdAndRoleNameIgnoreCase(managerId, MANAGER)
                    .orElseThrow(() -> new IllegalArgumentException("Selected manager does not exist"));
            if (!manager.isActive()) {
                throw new IllegalArgumentException("Selected manager must be active");
            }
            return manager;
        }
        if (managerId != null) {
            throw new IllegalArgumentException("Manager can only be assigned to SALES_REP");
        }
        return null;
    }

    private AppUser save(AppUser employee) {
        try {
            return appUserRepository.saveAndFlush(employee);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
    }

    private static EmployeeResponse toResponse(AppUser employee) {
        AppUser manager = employee.getManager();
        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getRole().getName().toUpperCase(Locale.ROOT),
                employee.isActive(),
                manager == null ? null : new EmployeeManagerResponse(
                        manager.getId(), manager.getFirstName(), manager.getLastName())
        );
    }

    private static void validateCreateRequest(EmployeeCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        validateCommon(request.firstName(), request.lastName(), request.email(), request.phone());
        requireLength(request.password(), "Password", 8, Integer.MAX_VALUE);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must contain at most 72 UTF-8 bytes");
        }
        if (isBlank(request.role())) {
            throw new IllegalArgumentException("Role is required");
        }
    }

    private static void validateUpdateRequest(EmployeeUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.role() != null || request.password() != null) {
            throw new IllegalArgumentException("Employee role and password cannot be changed");
        }
        validateCommon(request.firstName(), request.lastName(), request.email(), request.phone());
    }

    private static void validateCommon(String firstName, String lastName, String email, String phone) {
        requireLength(firstName, "First name", 1, 50);
        requireLength(lastName, "Last name", 1, 50);
        if (isBlank(email) || email.trim().length() > 255
                || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("A valid email is required");
        }
        if (phone != null && phone.trim().length() > 50) {
            throw new IllegalArgumentException("Phone must contain at most 50 characters");
        }
    }

    private static void requireLength(String value, String field, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        if (length < min || length > max) {
            throw new IllegalArgumentException(
                    field + " must contain between " + min + " and " + max + " characters");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeRole(String role) {
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireCeo(AppUserPrincipal principal) {
        if (principal == null || !principal.hasRole("CEO")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can manage employees");
        }
    }

    private Long resolveManagerId(AppUserPrincipal principal, Long requestedManagerId) {
        if (principal.hasRole("MANAGER")) {
            if (requestedManagerId != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managers cannot select managerId");
            }
            return principal.id();
        }
        if (principal.hasRole("CEO")) {
            if (requestedManagerId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "managerId is required");
            }
            appUserRepository.findByIdAndRoleNameIgnoreCase(requestedManagerId, "MANAGER")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manager not found"));
            return requestedManagerId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers and CEO can view a team summary");
    }

    private static DateRange currentMonthRange() {
        ZoneId zone = ZoneId.systemDefault();
        YearMonth currentMonth = YearMonth.now(zone);
        return new DateRange(
                currentMonth.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                currentMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime()
        );
    }

    private record DateRange(OffsetDateTime start, OffsetDateTime end) {
    }
}
