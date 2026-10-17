import { LogOut } from "lucide-react";
import { NavLink } from "react-router-dom";

import type { Role } from "../../../roles/role.types";
import { useAuth } from "../../../features/auth/hooks/useAuth";
import { APP_ROUTES } from "../../routes";

const ROLE_LABELS: Record<Role, string> = {
    SALES_REP: "Komercijalista",
    MANAGER: "Menadžer",
    CEO: "CEO",
    BOOKKEEPER: "Knjigovođa",
};

type HeaderProps = {
    role: Role;
    name: string;
};

export default function Header({ role, name }: HeaderProps) {
    const { logout } = useAuth();

    return (
        <header className="sticky top-0 z-40 border-b border-dust-grey bg-white/95 backdrop-blur">
            <div className="flex h-16 items-center justify-between px-4 sm:px-6 lg:px-8">
                <NavLink to={APP_ROUTES.home} className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-yale-blue font-black text-white">
                        {ROLE_LABELS[role].charAt(0).toUpperCase()}
                    </span>
                    <span className="flex flex-col">
                        <strong className="block leading-tight text-yale-blue">
                            {ROLE_LABELS[role]}
                        </strong>
                    </span>
                </NavLink>

                <div className="flex items-center gap-3 px-2 py-1.5">
                    <strong className="block text-sm">{name}</strong>
                    <span className="flex h-10 w-10 items-center justify-center rounded-full bg-stormy-teal font-bold text-white">
                        {name.charAt(0).toUpperCase()}
                    </span>
                    <button
                        type="button"
                        onClick={logout}
                        aria-label="Odjavi se"
                        title="Odjavi se"
                        className="rounded-lg p-2 text-graphite/60 transition hover:bg-dust-grey/40 hover:text-yale-blue focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                    >
                        <LogOut size={20} />
                    </button>
                </div>
            </div>
        </header>
    );
}
