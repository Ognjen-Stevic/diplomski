import type { LucideIcon } from "lucide-react";

import type { Permission } from "../../../roles/permissions";
import type { Role } from "../../../roles/role.types";

export type MobileNavigationPosition = 1 | 2 | 4;

export type NavigationItem = {
    id: string;
    label: string;
    route: string;
    icon: LucideIcon;
    permission?: Permission;
    roles?: readonly Role[];
    mobilePosition?: MobileNavigationPosition;
    exact?: boolean;
};
