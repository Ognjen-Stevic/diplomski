import { PERMISSIONS, type Permission } from "./permissions";
import type { Role } from "./role.types";

type PermissionChecker = (permission: Permission) => boolean;

export function canCreateGoodsRequest(
    role: Role | null | undefined,
    hasPermission: PermissionChecker,
) {
    return role === "SALES_REP" && hasPermission(PERMISSIONS.GOODS_REQUESTS_CREATE);
}

export function canApproveGoodsRequest(
    role: Role | null | undefined,
    hasPermission: PermissionChecker,
) {
    return role === "BOOKKEEPER" && hasPermission(PERMISSIONS.GOODS_REQUESTS_APPROVE);
}

export function canAssignAdvertisingMaterial(
    role: Role | null | undefined,
    hasPermission: PermissionChecker,
) {
    return role === "SALES_REP"
        && hasPermission(PERMISSIONS.ADVERTISING_MATERIALS_CREATE);
}
