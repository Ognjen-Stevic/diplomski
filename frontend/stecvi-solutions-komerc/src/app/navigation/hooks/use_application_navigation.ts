import { useMemo } from "react";

import { useAuth } from "../../../features/auth/hooks/useAuth";
import { applicationNavigation } from "../application_navigation";
import { canAccessNavigationItem } from "../navigation_access";
import type { NavigationItem } from "../types/navigation.types";

export function useApplicationNavigation() {
    const { user, hasPermission } = useAuth();

    return useMemo(() => {
        if (!user) {
            return {
                navigation: [] as NavigationItem[],
            };
        }

        const navigation: NavigationItem[] = applicationNavigation.filter((item) =>
            canAccessNavigationItem(item, user, hasPermission),
        );

        return {
            navigation,
        };
    }, [hasPermission, user]);
}
