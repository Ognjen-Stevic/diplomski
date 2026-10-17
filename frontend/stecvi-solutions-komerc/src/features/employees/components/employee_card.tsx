import { Mail, Pencil, Phone, Power, UserRound } from "lucide-react";
import type { Employee, EmployeeRole } from "../types/employee.types";

const ROLE_LABELS: Record<EmployeeRole, string> = {
    SALES_REP: "Komercijalista",
    MANAGER: "Menadžer",
    BOOKKEEPER: "Knjigovođa",
};

export function EmployeeCard({
    employee,
    changingStatus,
    onEdit,
    onToggleActive,
}: {
    employee: Employee;
    changingStatus: boolean;
    onEdit: () => void;
    onToggleActive: () => void;
}) {
    return (
        <article className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm">
            <div className="flex items-start gap-4">
                <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-yale-blue/10 text-yale-blue">
                    <UserRound className="h-6 w-6" />
                </span>
                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                            <h2 className="text-lg font-bold text-yale-blue">{employee.firstName} {employee.lastName}</h2>
                            <p className="mt-0.5 text-sm font-semibold text-stormy-teal">{ROLE_LABELS[employee.role]}</p>
                        </div>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${employee.active ? "bg-emerald-50 text-emerald-800 ring-emerald-200" : "bg-slate-100 text-slate-600 ring-slate-200"}`}>
                            {employee.active ? "Aktivan" : "Neaktivan"}
                        </span>
                    </div>
                    <p className="mt-4 flex items-center gap-2 break-all text-sm text-graphite/65"><Mail className="h-4 w-4 shrink-0" />{employee.email}</p>
                    <p className="mt-2 flex items-center gap-2 text-sm text-graphite/65"><Phone className="h-4 w-4 shrink-0" />{employee.phone || "Telefon nije unet"}</p>
                    {employee.role === "SALES_REP" && (
                        <p className="mt-3 text-sm text-graphite/60">Menadžer: <strong className="text-graphite">{employee.manager ? `${employee.manager.firstName} ${employee.manager.lastName}` : "Nije dodeljen"}</strong></p>
                    )}
                </div>
            </div>
            <div className="mt-5 grid grid-cols-3 gap-2 border-t border-dust-grey pt-4">
                <a
                    href={employee.phone ? `tel:${employee.phone.replace(/[^\d+]/g, "")}` : undefined}
                    aria-disabled={!employee.phone}
                    className={`inline-flex items-center justify-center gap-1.5 rounded-xl px-2 py-2.5 text-sm font-bold ${employee.phone ? "bg-yale-blue text-white hover:bg-stormy-teal" : "cursor-not-allowed bg-slate-100 text-slate-400"}`}
                ><Phone className="h-4 w-4" />Pozovi</a>
                <button type="button" onClick={onEdit} className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-yale-blue px-2 py-2.5 text-sm font-bold text-yale-blue hover:border-stormy-teal hover:text-stormy-teal"><Pencil className="h-4 w-4" />Izmeni</button>
                <button type="button" disabled={changingStatus} onClick={onToggleActive} className={`inline-flex items-center justify-center gap-1.5 rounded-xl border px-2 py-2.5 text-sm font-bold disabled:opacity-50 ${employee.active ? "border-red-200 text-red-700 hover:bg-red-50" : "border-emerald-200 text-emerald-700 hover:bg-emerald-50"}`}><Power className="h-4 w-4" />{employee.active ? "Deaktiviraj" : "Aktiviraj"}</button>
            </div>
        </article>
    );
}
