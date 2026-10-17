import { Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/hooks/useAuth";
import { PERMISSIONS } from "../../../roles/permissions";

export default function Header() {
    const navigate = useNavigate();
    const { hasPermission } = useAuth();

    return (
        <header className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
            <div>
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Evidencija kupaca</p>
                <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Kupci</h1>
            </div>

            {hasPermission(PERMISSIONS.CUSTOMERS_CREATE) && <button
                    type="button"
                    onClick={() => navigate("/kupci/novi")}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2 sm:w-auto"
                >
                    <Plus className="h-5 w-5" />
                    Novi kupac
            </button>}
        </header>
    )
}
