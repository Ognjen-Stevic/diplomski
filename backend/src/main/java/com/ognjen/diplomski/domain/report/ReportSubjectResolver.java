package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Presentation only: call after the report service has authorized the requested scope. */
@Component
@RequiredArgsConstructor
public class ReportSubjectResolver {
    private final AppUserRepository users;

    public String resolve(AppUserPrincipal requester, Long salesRepId, Long managerId) {
        if (salesRepId != null) {
            if (salesRepId.equals(requester.id())) return fullName(requester);
            return name(salesRepId, "SALES_REP");
        }
        if (requester.hasRole("CEO")) {
            return managerId == null ? "Cela kompanija" : "Tim menadžera " + name(managerId, "MANAGER");
        }
        if (requester.hasRole("MANAGER")) return "Tim menadžera " + fullName(requester);
        return fullName(requester);
    }

    private String name(Long id, String role) {
        AppUser user = users.findByIdAndRoleNameIgnoreCase(id, role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report subject not found"));
        return user.getFirstName() + " " + user.getLastName();
    }

    private static String fullName(AppUserPrincipal user) {
        return user.firstName() + " " + user.lastName();
    }
}
