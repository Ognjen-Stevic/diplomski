import { NavLink } from "react-router-dom";

import type { NavigationItem } from "../types/navigation.types";

export function DesktopMenuItem({ item }: { item: NavigationItem }) {
    const Icon = item.icon;

    return (
        <NavLink
            to={item.route}
            end={item.exact}
            className={({ isActive }) =>
                [
                    "flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-semibold transition",
                    "focus:outline-none focus:ring-2 focus:ring-stormy-teal/30",
                    isActive
                        ? "bg-yale-blue text-white shadow-sm"
                        : "text-graphite hover:bg-dust-grey/35 hover:text-yale-blue",
                ].join(" ")
            }
        >
            <Icon className="h-5 w-5" />
            {item.label}
        </NavLink>
    );
}
