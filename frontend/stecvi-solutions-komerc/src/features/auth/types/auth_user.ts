import type { Permission } from "../../../roles/permissions";
import type { Role } from "../../../roles/role.types";

export type AuthUser = {
    id: number;
    email: string;
    name: string;
    role: Role;
    permissions: Permission[];
};