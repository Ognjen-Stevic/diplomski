import type { Role } from "../../roles/role.types";
import type { Permission } from "../../roles/permissions";
import type { NavigationItem } from "./types/navigation.types";

type NavigationUser = {
    role: Role;
};

export function canAccessNavigationItem(
    item: NavigationItem,
    user: NavigationUser,
    hasPermission: (permission: Permission) => boolean,
) {
    const hasRequiredPermission = !item.permission || hasPermission(item.permission);
    const hasRequiredRole = !item.roles || item.roles.includes(user.role);

    return hasRequiredPermission && hasRequiredRole;
}
