import { SessionScope } from "../../../shared/components/session_scope";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { useCallback, useMemo, useRef, useState } from "react";
import { Plus, Search, UsersRound } from "lucide-react";
import { Navigate, useSearchParams } from "react-router-dom";
import AppLayout from "../../../app/layouts/app_layout";
import { useAuth } from "../../auth/hooks/useAuth";
import { PERMISSIONS } from "../../../roles/permissions";
import { EmployeeCard } from "../components/employee_card";
import { EmployeeForm } from "../components/employee_form";
import { EmployeeStatusConfirmation } from "../components/employee_status_confirmation";
import { employeesApi } from "../employees_api";
import type {
  Employee,
    EmployeeCreatePayload,
  EmployeeRole,
  EmployeeUpdatePayload,
} from "../types/employee.types";

export default function EmployeesPage() {
  return (
    <SessionScope>
      <EmployeesContent />
    </SessionScope>
  );
}
function EmployeesContent() {
  const { user, hasPermission } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const load = useCallback(async (signal: AbortSignal) => {
    const [employees, managers] = await Promise.all([
      employeesApi.findAll(signal),
      employeesApi.findActiveManagers(signal),
    ]);
    return { employees, managers };
  }, []);
  const query = useSessionRequest(
    "employees",
    load,
    user?.role === "CEO" && hasPermission(PERMISSIONS.EMPLOYEES_READ),
  );
  const employees = useMemo(() => query.data?.employees ?? [], [query.data]);
  const managers = query.data?.managers ?? [];
  const loading = query.loading;
  const [pageError, setPageError] = useState("");
  const guard = useRef(false);
  const [formError, setFormError] = useState("");
  const formOpen =
    searchParams.get("action") === "new" ||
    searchParams.get("action") === "edit";
  const setFormOpen = (open: boolean) =>
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete("employeeId");
      if (open) next.set("action", "new");
      else next.delete("action");
      return next;
    });
  const selected = employees.find(item => item.id === Number(searchParams.get("employeeId"))) ?? null;
  const [saving, setSaving] = useState(false);
  const [changingStatusId, setChangingStatusId] = useState<number | null>(null);
  const [statusConfirmation, setStatusConfirmation] = useState<Employee | null>(
    null,
  );
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<EmployeeRole | "ALL">("ALL");
  const [statusFilter, setStatusFilter] = useState<
    "ALL" | "ACTIVE" | "INACTIVE"
  >("ALL");

  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("sr-Latn-RS");
    return employees.filter((employee) => {
      const matchesSearch =
        !term ||
        `${employee.firstName} ${employee.lastName} ${employee.email}`
          .toLocaleLowerCase("sr-Latn-RS")
          .includes(term);
      const matchesRole = roleFilter === "ALL" || employee.role === roleFilter;
      const matchesStatus =
        statusFilter === "ALL" ||
        employee.active === (statusFilter === "ACTIVE");
      return matchesSearch && matchesRole && matchesStatus;
    });
  }, [employees, roleFilter, search, statusFilter]);

  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "CEO" || !hasPermission(PERMISSIONS.EMPLOYEES_READ))
    return <Navigate to="/" replace />;

  const closeForm = () => {
    if (!guard.current) {
      setFormOpen(false);

      setFormError("");
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current);
          next.delete("action");
          return next;
        },
        { replace: true },
      );
    }
  };
  const save = async (operation: () => Promise<unknown>) => {
    if (guard.current) return;
    guard.current = true;
    setSaving(true);
    setFormError("");
    setPageError("");
    try {
      await operation();
    } catch (error) {
      if (query.isCurrent())
        setFormError(
          error instanceof Error ? error.message : "Zaposleni nije sačuvan.",
        );
      return;
    } finally {
      guard.current = false;
      if (query.isCurrent()) setSaving(false);
    }
    if (!query.isCurrent()) return;
    setFormOpen(false);

    query.reload();
  };
  const submitCreate = (payload: EmployeeCreatePayload) =>
    save(() => employeesApi.create(payload));
  const submitUpdate = (payload: EmployeeUpdatePayload) => {
    const id = Number(searchParams.get("employeeId"));
    return Number.isSafeInteger(id) && id > 0
      ? save(() => employeesApi.update(id, payload))
      : Promise.resolve();
  };
  const changeStatus = async () => {
    if (guard.current || !statusConfirmation) return;
    guard.current = true;
    const employee = statusConfirmation;
    setChangingStatusId(employee.id);
    setPageError("");
    try {
      await employeesApi.setActive(employee.id, !employee.active);
      if (!query.isCurrent()) return;
      setStatusConfirmation(null);
      query.reload();
    } catch (error) {
      if (query.isCurrent())
        setPageError(
          error instanceof Error ? error.message : "Status nije promenjen.",
        );
    } finally {
      guard.current = false;
      if (query.isCurrent()) setChangingStatusId(null);
    }
  };
  return (
    <AppLayout>
      <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">
              CEO administracija
            </p>
            <h1 className="mt-1 text-3xl font-bold text-yale-blue">
              Zaposleni
            </h1>
            <p className="mt-2 text-graphite/60">
              Pregled i upravljanje komercijalistima, menadžerima i
              knjigovođama.
            </p>
          </div>
          <button
            type="button"
            onClick={() => {

              setFormError("");
              setFormOpen(true);
            }}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-bold text-white hover:bg-stormy-teal"
          >
            <Plus className="h-5 w-5" />
            Zaposli radnika
          </button>
        </div>
        <section className="mt-7 grid gap-3 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm sm:grid-cols-3">
          <label className="relative">
            <span className="sr-only">Pretraži zaposlene</span>
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-graphite/45" />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Ime, prezime ili email"
              className="w-full rounded-xl border border-dust-grey py-2.5 pl-10 pr-3 outline-none focus:border-stormy-teal"
            />
          </label>
          <select
            aria-label="Filtriraj po ulozi"
            value={roleFilter}
            onChange={(event) =>
              setRoleFilter(event.target.value as EmployeeRole | "ALL")
            }
            className="rounded-xl border border-dust-grey px-3 py-2.5 outline-none focus:border-stormy-teal"
          >
            <option value="ALL">Sve uloge</option>
            <option value="SALES_REP">Komercijalisti</option>
            <option value="MANAGER">Menadžeri</option>
            <option value="BOOKKEEPER">Knjigovođe</option>
          </select>
          <select
            aria-label="Filtriraj po statusu"
            value={statusFilter}
            onChange={(event) =>
              setStatusFilter(
                event.target.value as "ALL" | "ACTIVE" | "INACTIVE",
              )
            }
            className="rounded-xl border border-dust-grey px-3 py-2.5 outline-none focus:border-stormy-teal"
          >
            <option value="ALL">Svi statusi</option>
            <option value="ACTIVE">Aktivni</option>
            <option value="INACTIVE">Neaktivni</option>
          </select>
        </section>
        {((pageError && !statusConfirmation) || query.error) && (
          <div
            role="alert"
            className="mt-6 flex items-center justify-between rounded-xl bg-red-50 px-4 py-3 text-red-700"
          >
            <span>
              {pageError ||
                (query.error
                  ? `Podaci nisu ponovo učitani: ${query.error}`
                  : "")}
            </span>
            <button
              onClick={() => query.reload()}
              className="font-bold hover:underline"
            >
              Pokušaj ponovo
            </button>
          </div>
        )}
        {loading ? (
          <p className="mt-10 text-center text-graphite/60">
            Učitavanje zaposlenih...
          </p>
        ) : filtered.length === 0 ? (
          <div className="mt-10 flex flex-col items-center text-center text-graphite/55">
            <UsersRound className="h-10 w-10" />
            <p className="mt-3 font-semibold">Nema pronađenih zaposlenih.</p>
          </div>
        ) : (
          <div className="mt-7 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {filtered.map((employee) => (
              <EmployeeCard
                key={employee.id}
                employee={employee}
                changingStatus={changingStatusId === employee.id}
                onEdit={() => {

                  setFormError("");
                  setSearchParams((current) => {
                    const next = new URLSearchParams(current);
                    next.set("action", "edit");
                    next.set("employeeId", String(employee.id));
                    return next;
                  });
                }}
                onToggleActive={() => {
                  setPageError("");
                  setStatusConfirmation(employee);
                }}
              />
            ))}
          </div>
        )}
      </main>
      {formOpen && (searchParams.get("action") === "new" || selected !== null) && (
        <EmployeeForm
          key={`${searchParams.get("action")}:${searchParams.get("employeeId")}`}
          employee={searchParams.get("action") === "new" ? null : selected}
          managers={managers}
          saving={saving}
          error={formError}
          onCancel={closeForm}
          onCreate={submitCreate}
          onUpdate={submitUpdate}
        />
      )}
      {statusConfirmation && (
        <EmployeeStatusConfirmation
          error={pageError}
          employee={statusConfirmation}
          submitting={changingStatusId === statusConfirmation.id}
          onCancel={() => {
            if (!guard.current) setStatusConfirmation(null);
          }}
          onConfirm={changeStatus}
        />
      )}
    </AppLayout>
  );
}
