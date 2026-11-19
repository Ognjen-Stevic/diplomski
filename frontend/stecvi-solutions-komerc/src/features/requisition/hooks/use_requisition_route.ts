import { useLocation, useNavigate } from "react-router-dom";

import { APP_ROUTES } from "../../../app/routes";

type RequisitionRouteState = {
    returnTo?: unknown;
};

function currentDestination(pathname: string, search: string, hash: string) {
    return `${pathname}${search}${hash}`;
}

function isRequisitionDestination(value: unknown): value is string {
    if (typeof value !== "string") return false;
    const pathname = value.split("#", 1)[0].split("?", 1)[0];
    return pathname === APP_ROUTES.requisitions
        || pathname.startsWith(`${APP_ROUTES.requisitions}/`);
}

export function useOpenRequisitionEditor() {
    const location = useLocation();
    const navigate = useNavigate();

    return (requisitionId: number) => navigate(
        APP_ROUTES.editRequisition(requisitionId),
        {
            state: {
                returnTo: currentDestination(location.pathname, location.search, location.hash),
            } satisfies RequisitionRouteState,
        },
    );
}

export function useRequisitionReturnDestination() {
    const location = useLocation();
    const returnTo = (location.state as RequisitionRouteState | null)?.returnTo;

    return isRequisitionDestination(returnTo) ? returnTo : APP_ROUTES.requisitions;
}
