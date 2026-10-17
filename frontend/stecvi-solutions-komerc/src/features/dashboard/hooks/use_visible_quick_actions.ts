import { useMemo } from "react";

import { useAuth } from "../../auth/hooks/useAuth";
import { quickActions } from "../quick_actions";

export function useVisibleQuickActions() {
    const { user, hasPermission } = useAuth();

    return useMemo(() => {
        if (!user) return [];

        return quickActions.filter((action) => {
            const hasRequiredPermission = !action.permission
                || hasPermission(action.permission);
            const hasRequiredRole = action.roles
                ? action.roles.includes(user.role)
                : user.role !== "CEO" && user.role !== "MANAGER";

            return hasRequiredPermission && hasRequiredRole;
        });
    }, [hasPermission, user]);
}
