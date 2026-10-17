import { useEffect, useRef } from "react";
import { X, type LucideIcon } from "lucide-react";
import { NavLink } from "react-router-dom";

import { useApplicationNavigation } from "../../../../app/navigation/hooks/use_application_navigation";

export function MobileQuickActionMenu({ onClose }: { onClose: () => void }) {
    const { navigation } = useApplicationNavigation();
    const panelRef = useRef<HTMLElement>(null);
    const closeButtonRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        const previouslyFocused = document.activeElement;
        closeButtonRef.current?.focus();

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                onClose();
                return;
            }

            if (event.key !== "Tab") return;
            const focusable = panelRef.current?.querySelectorAll<HTMLElement>(
                "a[href], button:not([disabled]), [tabindex]:not([tabindex='-1'])",
            );
            if (!focusable?.length) return;

            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        };

        document.addEventListener("keydown", handleKeyDown);
        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
        };
    }, [onClose]);

    const menuItems = navigation
        .filter((item) => item.id !== "home")
        .map((item) => ({
            key: item.id,
            label: item.label,
            route: item.route,
            icon: item.icon,
            exact: item.exact,
        }));

    return (
        <div
            className="fixed inset-0 z-50 lg:hidden"
            role="dialog"
            aria-modal="true"
            aria-labelledby="mobile-actions-title"
        >
            <button
                type="button"
                aria-label="Zatvori meni"
                onClick={onClose}
                className="absolute inset-0 bg-graphite/45"
            />

            <section
                ref={panelRef}
                className="absolute inset-x-0 bottom-0 max-h-[calc(100dvh-1rem)] overflow-y-auto overscroll-contain rounded-t-3xl bg-white p-5 pb-[max(1.5rem,env(safe-area-inset-bottom))] shadow-2xl"
            >
                <div className="mb-5 flex items-center justify-between">
                    <h2 id="mobile-actions-title" className="text-lg font-bold text-yale-blue">
                        Meni
                    </h2>
                    <button
                        ref={closeButtonRef}
                        type="button"
                        onClick={onClose}
                        className="min-h-11 min-w-11 rounded-lg p-2 text-graphite hover:bg-dust-grey/35"
                        aria-label="Zatvori"
                    >
                        <X className="mx-auto h-5 w-5" />
                    </button>
                </div>

                {menuItems.length > 0 ? (
                    <div className="grid grid-cols-2 gap-3">
                        {menuItems.map((item) => (
                            <MenuItem
                                key={item.key}
                                label={item.label}
                                route={item.route}
                                icon={item.icon}
                                exact={item.exact}
                                onClose={onClose}
                            />
                        ))}
                    </div>
                ) : (
                    <p className="rounded-xl bg-dust-grey/25 px-4 py-3 text-sm text-graphite/70">
                        Nemate dostupnih funkcionalnosti.
                    </p>
                )}
            </section>
        </div>
    );
}

function MenuItem({
    label,
    route,
    icon: Icon,
    exact,
    onClose,
}: {
    label: string;
    route: string;
    icon: LucideIcon;
    exact?: boolean;
    onClose: () => void;
}) {
    return (
        <NavLink
            to={route}
            end={exact}
            onClick={onClose}
            className="flex min-h-28 flex-col rounded-2xl border border-dust-grey p-4 shadow-sm transition hover:border-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
        >
            <span className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-dust-grey/35 text-yale-blue">
                <Icon className="h-5 w-5" />
            </span>
            <strong className="line-clamp-2 min-h-10 text-sm leading-5 text-graphite">
                {label}
            </strong>
        </NavLink>
    );
}
