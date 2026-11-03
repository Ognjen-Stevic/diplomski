import { useDialog } from "../../../shared/hooks/use_dialog";
import { useMemo, useState, type FormEvent } from "react";
import { Save, X } from "lucide-react";
import type { Employee, EmployeeCreatePayload, EmployeeManager, EmployeeUpdatePayload } from "../types/employee.types";

const inputClass = "mt-2 w-full rounded-xl border border-dust-grey bg-white px-4 py-3 text-graphite outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20";

export function EmployeeForm({ employee, managers, saving, error, onCancel, onCreate, onUpdate }: {
    employee: Employee | null;
    managers: EmployeeManager[];
    saving: boolean;
    error: string;
    onCancel: () => void;
    onCreate: (payload: EmployeeCreatePayload) => Promise<void>;
    onUpdate: (payload: EmployeeUpdatePayload) => Promise<void>;
}) {
    const dialog = useDialog(onCancel, saving);
    const [firstName, setFirstName] = useState(employee?.firstName ?? "");
    const [lastName, setLastName] = useState(employee?.lastName ?? "");
    const [email, setEmail] = useState(employee?.email ?? "");
    const [phone, setPhone] = useState(employee?.phone ?? "");
    const [password, setPassword] = useState("");
    const [role, setRole] = useState<"SALES_REP" | "MANAGER" | "BOOKKEEPER">(
        employee?.role === "MANAGER" ? "MANAGER" : "SALES_REP",
    );
    const [managerId, setManagerId] = useState(employee?.manager ? String(employee.manager.id) : "");

    const managerOptions = useMemo(() => {
        if (!employee?.manager || managers.some((manager) => manager.id === employee.manager?.id)) return managers;
        return [employee.manager, ...managers];
    }, [employee, managers]);
    const showManager = employee ? employee.role === "SALES_REP" : role === "SALES_REP";

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        const common = {
            firstName: firstName.trim(),
            lastName: lastName.trim(),
            email: email.trim(),
            phone: phone.trim() || null,
            managerId: showManager ? Number(managerId) : null,
        };
        if (employee) await onUpdate(common);
        else await onCreate({ ...common, password, role });
    };

    return (
        <div ref={dialog} tabIndex={-1} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4" role="dialog" aria-modal="true" aria-labelledby="employee-form-title">
            <form onSubmit={submit} className="max-h-[calc(100vh-2rem)] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl sm:p-7">
                <div className="flex items-start justify-between gap-4">
                    <div><p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Upravljanje zaposlenima</p><h2 id="employee-form-title" className="mt-1 text-2xl font-bold text-yale-blue">{employee ? "Izmeni zaposlenog" : "Novi zaposleni"}</h2></div>
                    <button type="button" onClick={onCancel} aria-label="Zatvori" className="rounded-lg p-2 text-graphite/60 hover:bg-dust-grey/50"><X className="h-5 w-5" /></button>
                </div>
                <div className="mt-6 grid gap-5 sm:grid-cols-2">
                    <Field label="Ime *" value={firstName} onChange={setFirstName} maxLength={50} />
                    <Field label="Prezime *" value={lastName} onChange={setLastName} maxLength={50} />
                    <Field label="Email *" type="email" value={email} onChange={setEmail} maxLength={255} />
                    <Field label="Telefon" type="tel" value={phone} onChange={setPhone} maxLength={50} required={false} />
                    {!employee && <Field label="Lozinka *" type="password" value={password} onChange={setPassword} minLength={8} maxLength={72} />}
                    {!employee && <label className="font-semibold text-graphite">Uloga *<select value={role} onChange={(event) => { const nextRole = event.target.value as "SALES_REP" | "MANAGER" | "BOOKKEEPER"; setRole(nextRole); if (nextRole !== "SALES_REP") setManagerId(""); }} className={inputClass}><option value="SALES_REP">Komercijalista</option><option value="MANAGER">Menadžer</option><option value="BOOKKEEPER">Knjigovođa</option></select></label>}
                    {employee && <div><p className="font-semibold text-graphite">Uloga</p><p className="mt-2 rounded-xl bg-dust-grey/35 px-4 py-3">{employee.role === "SALES_REP" ? "Komercijalista" : employee.role === "MANAGER" ? "Menadžer" : "Knjigovođa"}</p></div>}
                    {showManager && <label className="font-semibold text-graphite sm:col-span-2">Menadžer *<select required value={managerId} onChange={(event) => setManagerId(event.target.value)} className={inputClass}><option value="">Izaberite menadžera</option>{managerOptions.map((manager) => <option key={manager.id} value={manager.id}>{manager.firstName} {manager.lastName}</option>)}</select></label>}
                </div>
                {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
                <div className="mt-7 flex justify-end gap-3"><button type="button" onClick={onCancel} className="rounded-xl border border-dust-grey px-5 py-3 font-semibold">Otkaži</button><button disabled={saving} type="submit" className="inline-flex items-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white hover:bg-stormy-teal disabled:opacity-60"><Save className="h-5 w-5" />{saving ? "Čuvanje..." : "Sačuvaj"}</button></div>
            </form>
        </div>
    );
}

function Field({ label, value, onChange, type = "text", required = true, minLength, maxLength }: { label: string; value: string; onChange: (value: string) => void; type?: string; required?: boolean; minLength?: number; maxLength?: number }) {
    return <label className="font-semibold text-graphite">{label}<input required={required} type={type} value={value} minLength={minLength} maxLength={maxLength} onChange={(event) => onChange(event.target.value)} className={inputClass} /></label>;
}
