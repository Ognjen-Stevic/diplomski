import { ChevronRight } from "lucide-react";
import { NavLink } from "react-router-dom";
import type { QuickAction } from "../../quick_actions";

export function QuickActionCard({
    action,
    className = "",
}: {
    action: QuickAction;
    className?: string;
}) {
    const Icon = action.icon;

    return (
        <NavLink
            to={action.route}
            className={[
                "group flex min-h-36 flex-col justify-between rounded-2xl border p-5 transition",
                "focus:outline-none focus:ring-2 focus:ring-stormy-teal/30",
                action.primary
                    ? "border-yale-blue bg-yale-blue text-white shadow-md hover:bg-stormy-teal"
                    : "border-dust-grey bg-white text-graphite shadow-sm hover:-translate-y-0.5 hover:border-stormy-teal hover:shadow-md",
                className,
            ].join(" ")}
        >
            <span
                className={[
                    "flex h-11 w-11 items-center justify-center rounded-xl",
                    action.primary
                        ? "bg-white/15 text-white"
                        : "bg-dust-grey/35 text-yale-blue",
                ].join(" ")}
            >
                <Icon className="h-6 w-6" />
            </span>

            <span className="mt-5 flex items-end justify-between gap-4">
                <span>
                    <strong className="block">{action.label}</strong>
                    <span
                        className={
                            action.primary
                                ? "mt-1 block text-sm text-white/75"
                                : "mt-1 block text-sm text-graphite/65"
                        }
                    >
                        {action.description}
                    </span>
                </span>
                <ChevronRight className="h-5 w-5 shrink-0 transition group-hover:translate-x-1" />
            </span>
        </NavLink>
    );
}
