import type { AuthUser } from "../../auth/types/auth_user";
import type { ReportSalesRepresentative } from "../types/sales_report.types";

export function reportSubject(
    user: AuthUser,
    salesRepId: number | null,
    managerId: number | null,
    representative: ReportSalesRepresentative | null,
    teamNames: Record<number, string>,
    managerNames: Record<number, string>,
) {
    if (salesRepId !== null) {
        const ownName = user.role === "SALES_REP" && salesRepId === user.id ? user.name : null;
        const responseName = representative?.id === salesRepId
            ? `${representative.firstName} ${representative.lastName}`.trim() : null;
        return { label: "Komercijalista", name: responseName || teamNames[salesRepId] || ownName
            || `Izabrani komercijalista (ID: ${salesRepId})` };
    }
    if (managerId !== null) {
        const ownName = user.role === "MANAGER" && managerId === user.id ? user.name : null;
        return { label: "Tim menadžera", name: managerNames[managerId] || ownName
            || `Izabrani tim (ID: ${managerId})` };
    }
    if (user.role === "SALES_REP") return { label: "Komercijalista", name: user.name };
    if (user.role === "MANAGER") return { label: "Tim menadžera", name: user.name };
    if (user.role === "CEO") return { label: "Obuhvat izveštaja", name: "Cela kompanija" };
    return { label: "Obuhvat izveštaja", name: "Izabrani obuhvat" };
}
