import { useCallback, useEffect, useMemo, useState } from "react";
import { Plus, Search, UsersRound } from "lucide-react";
import { Navigate, useSearchParams } from "react-router-dom";
import AppLayout from "../../../app/layouts/app_layout";
import { useAuth } from "../../auth/hooks/useAuth";
import { PERMISSIONS } from "../../../roles/permissions";
import { EmployeeCard } from "../components/employee_card";
import { EmployeeForm } from "../components/employee_form";
import { EmployeeStatusConfirmation } from "../components/employee_status_confirmation";
import { employeesApi } from "../employees_api";
import type { Employee, EmployeeCreatePayload, EmployeeManager, EmployeeRole, EmployeeUpdatePayload } from "../types/employee.types";

export default function EmployeesPage() {
    const { user, hasPermission } = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();
    const [employees, setEmployees] = useState<Employee[]>([]);
    const [managers, setManagers] = useState<EmployeeManager[]>([]);
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [formError, setFormError] = useState("");
    const [formOpen, setFormOpen] = useState(() => searchParams.get("action") === "new");
    const [selected, setSelected] = useState<Employee | null>(null);
    const [saving, setSaving] = useState(false);
    const [changingStatusId, setChangingStatusId] = useState<number | null>(null);
    const [statusConfirmation, setStatusConfirmation] = useState<Employee | null>(null);
    const [search, setSearch] = useState("");
    const [roleFilter, setRoleFilter] = useState<EmployeeRole | "ALL">("ALL");
    const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");

    const load = useCallback(async () => {
        setLoading(true); setPageError("");
        try {
            const [employeeData, managerData] = await Promise.all([employeesApi.findAll(), employeesApi.findActiveManagers()]);
            setEmployees(employeeData); setManagers(managerData);
        } catch (error) { setPageError(error instanceof Error ? error.message : "Zaposleni nisu učitani."); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => {
        if (user?.role !== "CEO" || !hasPermission(PERMISSIONS.EMPLOYEES_READ)) return;
        let active = true;
        Promise.all([employeesApi.findAll(), employeesApi.findActiveManagers()])
            .then(([employeeData, managerData]) => {
                if (!active) return;
                setEmployees(employeeData);
                setManagers(managerData);
            })
            .catch((error: unknown) => {
                if (active) setPageError(error instanceof Error ? error.message : "Zaposleni nisu učitani.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [hasPermission, user]);

    const filtered = useMemo(() => {
        const term = search.trim().toLocaleLowerCase("sr-Latn-RS");
        return employees.filter((employee) => {
            const matchesSearch = !term || `${employee.firstName} ${employee.lastName} ${employee.email}`.toLocaleLowerCase("sr-Latn-RS").includes(term);
            const matchesRole = roleFilter === "ALL" || employee.role === roleFilter;
            const matchesStatus = statusFilter === "ALL" || employee.active === (statusFilter === "ACTIVE");
            return matchesSearch && matchesRole && matchesStatus;
        });
    }, [employees, roleFilter, search, statusFilter]);

    if (!user) return <Navigate to="/login" replace />;
    if (user.role !== "CEO" || !hasPermission(PERMISSIONS.EMPLOYEES_READ)) return <Navigate to="/" replace />;

    const closeForm = () => {
        if (!saving) {
            setFormOpen(false);
            setSelected(null);
            setFormError("");
            setSearchParams((current) => {
                const next = new URLSearchParams(current);
                next.delete("action");
                return next;
            }, { replace: true });
        }
    };
    const submitCreate = async (payload: EmployeeCreatePayload) => {
        setSaving(true); setFormError("");
        try { const created = await employeesApi.create(payload); setEmployees((current) => [...current, created]); await refreshManagers(); closeForm(); }
        catch (error) { setFormError(error instanceof Error ? error.message : "Zaposleni nije sačuvan."); }
        finally { setSaving(false); }
    };
    const submitUpdate = async (payload: EmployeeUpdatePayload) => {
        if (!selected) return;
        setSaving(true); setFormError("");
        try { const updated = await employeesApi.update(selected.id, payload); setEmployees((current) => current.map((item) => item.id === updated.id ? updated : item)); await refreshManagers(); setFormOpen(false); setSelected(null); }
        catch (error) { setFormError(error instanceof Error ? error.message : "Izmene nisu sačuvane."); }
        finally { setSaving(false); }
    };
    const refreshManagers = async () => setManagers(await employeesApi.findActiveManagers());

    return <AppLayout><main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">CEO administracija</p><h1 className="mt-1 text-3xl font-bold text-yale-blue">Zaposleni</h1><p className="mt-2 text-graphite/60">Pregled i upravljanje komercijalistima, menadžerima i knjigovođama.</p></div><button type="button" onClick={() => { setSelected(null); setFormError(""); setFormOpen(true); }} className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-bold text-white hover:bg-stormy-teal"><Plus className="h-5 w-5" />Zaposli radnika</button></div>
        <section className="mt-7 grid gap-3 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm sm:grid-cols-3">
            <label className="relative"><span className="sr-only">Pretraži zaposlene</span><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-graphite/45" /><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Ime, prezime ili email" className="w-full rounded-xl border border-dust-grey py-2.5 pl-10 pr-3 outline-none focus:border-stormy-teal" /></label>
            <select aria-label="Filtriraj po ulozi" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value as EmployeeRole | "ALL")} className="rounded-xl border border-dust-grey px-3 py-2.5 outline-none focus:border-stormy-teal"><option value="ALL">Sve uloge</option><option value="SALES_REP">Komercijalisti</option><option value="MANAGER">Menadžeri</option><option value="BOOKKEEPER">Knjigovođe</option></select>
            <select aria-label="Filtriraj po statusu" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as "ALL" | "ACTIVE" | "INACTIVE")} className="rounded-xl border border-dust-grey px-3 py-2.5 outline-none focus:border-stormy-teal"><option value="ALL">Svi statusi</option><option value="ACTIVE">Aktivni</option><option value="INACTIVE">Neaktivni</option></select>
        </section>
        {pageError && <div role="alert" className="mt-6 flex items-center justify-between rounded-xl bg-red-50 px-4 py-3 text-red-700"><span>{pageError}</span><button onClick={() => void load()} className="font-bold hover:underline">Pokušaj ponovo</button></div>}
        {loading ? <p className="mt-10 text-center text-graphite/60">Učitavanje zaposlenih...</p> : filtered.length === 0 ? <div className="mt-10 flex flex-col items-center text-center text-graphite/55"><UsersRound className="h-10 w-10" /><p className="mt-3 font-semibold">Nema pronađenih zaposlenih.</p></div> : <div className="mt-7 grid gap-5 md:grid-cols-2 xl:grid-cols-3">{filtered.map((employee) => <EmployeeCard key={employee.id} employee={employee} changingStatus={changingStatusId === employee.id} onEdit={() => { setSelected(employee); setFormError(""); setFormOpen(true); }} onToggleActive={() => setStatusConfirmation(employee)} />)}</div>}
    </main>{formOpen && <EmployeeForm employee={selected} managers={managers} saving={saving} error={formError} onCancel={closeForm} onCreate={submitCreate} onUpdate={submitUpdate} />}{statusConfirmation && <EmployeeStatusConfirmation employee={statusConfirmation} submitting={changingStatusId === statusConfirmation.id} onCancel={() => { if (changingStatusId === null) setStatusConfirmation(null); }} onConfirm={async () => { const employee = statusConfirmation; setChangingStatusId(employee.id); setPageError(""); try { const updated = await employeesApi.setActive(employee.id, !employee.active); setEmployees((current) => current.map((item) => item.id === updated.id ? updated : item)); await refreshManagers(); setStatusConfirmation(null); } catch (error) { setPageError(error instanceof Error ? error.message : "Status nije promenjen."); setStatusConfirmation(null); } finally { setChangingStatusId(null); } }} />}</AppLayout>;
}
