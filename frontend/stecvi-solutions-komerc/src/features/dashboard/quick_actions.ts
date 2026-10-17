import {
    BarChart3,
    Box,
    ClipboardCheck,
    ClipboardList,
    Package,
    PackagePlus,
    PackageSearch,
    UserPlus,
    Users,
    UsersRound,
    type LucideIcon,
} from "lucide-react";

import { APP_ROUTES } from "../../app/routes";
import { PERMISSIONS, type Permission } from "../../roles/permissions";
import type { Role } from "../../roles/role.types";

export type QuickAction = {
    label: string;
    description: string;
    route: string;
    icon: LucideIcon;
    permission?: Permission;
    roles?: readonly Role[];
    primary?: boolean;
};

export const quickActions: QuickAction[] = [
    {
        label: "Novi kupac",
        description: "Unesite podatke o novom objektu.",
        route: APP_ROUTES.newCustomer,
        icon: Users,
        permission: PERMISSIONS.CUSTOMERS_CREATE,
    },
    {
        label: "Novo trebovanje",
        description: "Uradite novo trebovanje za objekat.",
        route: APP_ROUTES.newRequisition,
        icon: ClipboardList,
        permission: PERMISSIONS.REQUISITIONS_CREATE,
        primary: true,
    },
    {
        label: "Zahtev za robu",
        description: "Rezervišite robu iz magacina.",
        route: APP_ROUTES.newGoodsRequest,
        icon: Box,
        permission: PERMISSIONS.GOODS_REQUESTS_CREATE,
        roles: ["SALES_REP"],
    },
    {
        label: "Reklamni materijal",
        description: "Zabeležite reklamni materijal dat kupcu.",
        route: APP_ROUTES.newAdvertisingMaterialDistribution,
        icon: Package,
        permission: PERMISSIONS.ADVERTISING_MATERIALS_CREATE,
        roles: ["SALES_REP"],
    },
    {
        label: "Trebovanja za obradu",
        description: "Pregledajte odobrena trebovanja koja čekaju obradu.",
        route: `${APP_ROUTES.requisitions}?status=approved`,
        icon: ClipboardCheck,
        permission: PERMISSIONS.REQUISITIONS_READ,
        roles: ["BOOKKEEPER"],
        primary: true,
    },
    {
        label: "Zahtevi za robu",
        description: "Odobrite ili odbijte zahteve komercijalista.",
        route: APP_ROUTES.goodsRequests,
        icon: Box,
        permission: PERMISSIONS.GOODS_REQUESTS_APPROVE,
        roles: ["BOOKKEEPER"],
    },
    {
        label: "Pregled artikala i cena",
        description: "Pregledajte cene, količine i stimulacije.",
        route: APP_ROUTES.products,
        icon: PackageSearch,
        roles: ["BOOKKEEPER"],
    },
    {
        label: "Zaposli radnika",
        description: "Dodajte novog zaposlenog u sistem.",
        route: `${APP_ROUTES.employees}?action=new`,
        icon: UserPlus,
        permission: PERMISSIONS.EMPLOYEES_READ,
        roles: ["CEO"],
        primary: true,
    },
    {
        label: "Novi artikal",
        description: "Registrujte novi artikal u sistemu.",
        route: `${APP_ROUTES.products}?action=new`,
        icon: PackagePlus,
        roles: ["CEO"],
    },
    {
        label: "Pregled trebovanja",
        description: "Pregledajte sva trebovanja u sistemu.",
        route: APP_ROUTES.requisitions,
        icon: ClipboardCheck,
        permission: PERMISSIONS.REQUISITIONS_READ,
        roles: ["CEO"],
    },
    {
        label: "Novi reklamni materijal",
        description: "Dodajte novi reklamni materijal.",
        route: `${APP_ROUTES.advertisingMaterialManagement}?action=new`,
        icon: Package,
        roles: ["CEO"],
    },
    {
        label: "Pregled svih trebovanja",
        description: "Pregledajte trebovanja svog tima.",
        route: APP_ROUTES.requisitions,
        icon: ClipboardCheck,
        permission: PERMISSIONS.REQUISITIONS_READ,
        roles: ["MANAGER"],
        primary: true,
    },
    {
        label: "Pregled kupaca",
        description: "Pregledajte kupce svog tima.",
        route: APP_ROUTES.customers,
        icon: Users,
        permission: PERMISSIONS.CUSTOMERS_READ,
        roles: ["MANAGER"],
    },
    {
        label: "Pregled tima",
        description: "Pregledajte članove i učinak svog tima.",
        route: APP_ROUTES.team,
        icon: UsersRound,
        permission: PERMISSIONS.EMPLOYEES_READ,
        roles: ["MANAGER"],
    },
    {
        label: "Izveštaj tima",
        description: "Otvorite mesečne i godišnje izveštaje tima.",
        route: APP_ROUTES.reports,
        icon: BarChart3,
        permission: PERMISSIONS.SALES_REPORTS_READ,
        roles: ["MANAGER"],
    },
];
