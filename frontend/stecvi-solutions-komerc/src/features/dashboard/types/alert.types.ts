import type { Role } from "../../../roles/role.types";
import { APP_ROUTES } from "../../../app/routes";

export type DashboardAlertCounters = {
    draftCustomers: number;
    requisitionAlerts: number;
    pendingGoodsRequests: number;
};

export type DashboardAlertDefinition = {
    key: keyof DashboardAlertCounters;
    label: string;
    roles: Role[];
    route: string;
};

export const dashboardAlertDefinitions: DashboardAlertDefinition[] = [
    {
        key: "draftCustomers",
        label: "Zahtevi za nove kupce",
        roles: ["MANAGER"],
        route: APP_ROUTES.customers,
    },
    {
        key: "requisitionAlerts",
        label: "Odbijena trebovanja",
        roles: ["SALES_REP"],
        route: APP_ROUTES.requisitions,
    },
    {
        key: "requisitionAlerts",
        label: "Trebovanja za odobrenje ili ponovni pregled",
        roles: ["MANAGER"],
        route: APP_ROUTES.requisitions,
    },
    {
        key: "requisitionAlerts",
        label: "Odobrena trebovanja",
        roles: ["BOOKKEEPER"],
        route: `${APP_ROUTES.requisitions}?status=approved`,
    },
    {
        key: "pendingGoodsRequests",
        label: "Zahtevi za robu na čekanju",
        roles: ["BOOKKEEPER"],
        route: APP_ROUTES.goodsRequests,
    },
];
