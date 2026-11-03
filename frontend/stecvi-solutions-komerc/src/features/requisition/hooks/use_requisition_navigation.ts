import { useSearchParams } from "react-router-dom";
import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";
import type { RequisitionStatus } from "../types/requisition.types";

export function useRequisitionNavigation() {
    const [search, setSearch] = useSearchParams();
    const now = new Date();
    const yearValue = Number(search.get("year"));
    const monthValue = Number(search.get("month"));
    const year = Number.isInteger(yearValue) && yearValue >= 2000 && yearValue <= 2100 ? yearValue : now.getFullYear();
    const month = Number.isInteger(monthValue) && monthValue >= 1 && monthValue <= 12 ? monthValue : now.getMonth() + 1;
    const requested = search.get("status");
    const status: RequisitionStatus | "ALL" = requested && Object.hasOwn(REQUISITION_STATUS_LABELS, requested)
        ? requested as RequisitionStatus : "ALL";
    const update = (date: Date, nextStatus: RequisitionStatus | "ALL") => {
        setSearch((current) => {
            const next = new URLSearchParams(current);
            next.set("year", String(date.getFullYear()));
            next.set("month", String(date.getMonth() + 1));
            if (nextStatus === "ALL") next.delete("status");
            else next.set("status", nextStatus);
            return next;
        });
    };
    return { date: new Date(year, month - 1, 1), year, month, status, update };
}
