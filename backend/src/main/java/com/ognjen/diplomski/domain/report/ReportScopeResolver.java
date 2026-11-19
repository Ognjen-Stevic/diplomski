package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ReportScopeResolver {

    private final AppUserRepository appUserRepository;

    public ReportScopeResolver(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public ReportScope resolve(
            AppUserPrincipal principal,
            Long requestedSalesRepId,
            Long requestedManagerId
    ) {
        if (principal.hasRole("CEO")) {
            if (requestedManagerId == null) {
                if (requestedSalesRepId == null) {
                    return new ReportScope(null, null, null, false, false, true);
                }
                SalesRepresentativeOptionResponse salesRep = findSalesRepresentative(requestedSalesRepId);
                return new ReportScope(requestedSalesRepId, salesRep, null, false, false, false);
            }

            Long managerId = resolveManagerId(principal, requestedManagerId);
            if (requestedSalesRepId == null) {
                return new ReportScope(null, null, managerId, true, true, false);
            }
            SalesRepresentativeOptionResponse salesRep = findSalesRepresentative(requestedSalesRepId, managerId);
            return new ReportScope(requestedSalesRepId, salesRep, managerId, false, true, false);
        }

        if (principal.hasRole("MANAGER")) {
            Long managerId = resolveManagerId(principal, requestedManagerId);
            if (requestedSalesRepId == null) {
                return new ReportScope(null, null, managerId, true, true, false);
            }
            SalesRepresentativeOptionResponse salesRep = findSalesRepresentative(requestedSalesRepId, managerId);
            return new ReportScope(requestedSalesRepId, salesRep, managerId, false, true, false);
        }

        if (requestedManagerId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can select a manager");
        }
        if (requestedSalesRepId != null && !requestedSalesRepId.equals(principal.id())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "A sales representative can only view their own report"
            );
        }
        return new ReportScope(
                principal.id(),
                new SalesRepresentativeOptionResponse(
                        principal.id(),
                        principal.firstName(),
                        principal.lastName()
                ),
                null,
                false,
                false,
                false
        );
    }

    public Long resolveManagerId(AppUserPrincipal principal, Long requestedManagerId) {
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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers and CEO can access team reports");
    }

    public void requireManagersComparisonAccess(AppUserPrincipal principal) {
        if (!principal.hasRole("CEO")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can compare managers");
        }
    }

    private SalesRepresentativeOptionResponse findSalesRepresentative(Long salesRepId) {
        return appUserRepository.findByIdAndRoleNameIgnoreCase(salesRepId, "SALES_REP")
                .map(SalesRepresentativeOptionResponse::from)
                .orElseThrow(this::salesRepresentativeNotFound);
    }

    private SalesRepresentativeOptionResponse findSalesRepresentative(Long salesRepId, Long managerId) {
        return appUserRepository.findByIdAndManagerIdAndRoleNameIgnoreCase(salesRepId, managerId, "SALES_REP")
                .map(SalesRepresentativeOptionResponse::from)
                .orElseThrow(this::salesRepresentativeNotFound);
    }

    private ResponseStatusException salesRepresentativeNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales representative not found");
    }

    public record ReportScope(
            Long salesRepId,
            SalesRepresentativeOptionResponse salesRep,
            Long managerId,
            boolean teamReport,
            boolean managerStimulation,
            boolean organizationReport
    ) {
    }
}
