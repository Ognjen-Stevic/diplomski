import { NavLink } from "react-router-dom";

import type { NavigationItem } from "../types/navigation.types";

export function MobileNavItem({
    item,
    onNavigate,
}: {
    item: NavigationItem;
    onNavigate?: () => void;
}) {
    const Icon = item.icon;

    return (
        <NavLink
            to={item.route}
            end={item.exact}
            onClick={onNavigate}
            className={({ isActive }) =>
                [
                    "flex h-14 flex-col items-center gap-1 rounded-lg px-1 py-1 text-xs font-medium",
                    "focus:outline-none focus:ring-2 focus:ring-stormy-teal/30",
                    isActive ? "text-yale-blue" : "text-graphite/60",
                ].join(" ")
            }
        >
            <Icon className="h-5 w-5 shrink-0" />
            <span className="flex h-6 max-w-full items-center justify-center">
                <span className="line-clamp-2 text-center text-[11px] leading-3">
                    {item.label}
                </span>
            </span>
        </NavLink>
    );
}
