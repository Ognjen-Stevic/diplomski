package com.ognjen.diplomski.security;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.security.AppUserPrincipal;
import java.util.Set;
/** Shared customer visibility policy, unchanged from CustomerServiceImpl. */
public final class CustomerAccessPolicy {
    private CustomerAccessPolicy() {}
    public static Set<CustomerStatus> visibleStatuses(AppUserPrincipal principal) {
        if (principal.hasRole("CEO") || principal.hasRole("MANAGER")) {
            return Set.of(
                    CustomerStatus.DRAFT,
                    CustomerStatus.APPROVED,
                    CustomerStatus.DEACTIVATED
            );
        }
        if (principal.hasRole("SALES_REP")) {
            return Set.of(CustomerStatus.DRAFT, CustomerStatus.APPROVED);
        }
        return Set.of();
    }

    public static CustomerAccessScope customerAccessScope(AppUserPrincipal principal) {
        return new CustomerAccessScope(
                principal.hasRole("CEO"),
                principal.hasRole("MANAGER"),
                principal.hasRole("SALES_REP")
        );
    }

    public static boolean canAccessCustomer(AppUserPrincipal principal, Customer customer) {
        if (principal.hasRole("CEO")) return true;

        if (principal.hasRole("MANAGER")) {
            return customer.getSalesRep().getManager() != null
                    && customer.getSalesRep().getManager().getId().equals(principal.id());
        }

        return principal.hasRole("SALES_REP")
                && customer.getSalesRep().getId().equals(principal.id());
    }

    public record CustomerAccessScope(boolean allCustomers, boolean managedSalesReps, boolean ownCustomers) {}
}
