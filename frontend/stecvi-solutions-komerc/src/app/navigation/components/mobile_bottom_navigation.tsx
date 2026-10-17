import { useCallback, useMemo, useState } from "react";
import { Plus } from "lucide-react";

import { MobileQuickActionMenu } from "../../../features/dashboard/components/quick_action_section/mobile_quick_action_menu";
import { useVisibleQuickActions } from "../../../features/dashboard/hooks/use_visible_quick_actions";
import type { QuickAction } from "../../../features/dashboard/quick_actions";
import type {
    MobileNavigationPosition,
    NavigationItem,
} from "../types/navigation.types";
import { MobileNavItem } from "./mobile_nav_item";

type MobileBottomNavigationProps = {
    primaryNavigation: NavigationItem[];
};

export function MobileBottomNavigation({
    primaryNavigation,
}: MobileBottomNavigationProps) {
    const [actionsOpen, setActionsOpen] = useState(false);
    const closeActions = useCallback(() => setActionsOpen(false), []);
    const quickActions = useVisibleQuickActions();
    const items = useMemo(
        () => selectBottomNavigationItems(primaryNavigation, quickActions),
        [primaryNavigation, quickActions],
    );

    return (
        <>
            <nav
                className="fixed inset-x-0 bottom-0 z-40 border-t border-dust-grey bg-white/95 px-3 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 backdrop-blur lg:hidden"
                aria-label="Mobilna navigacija"
            >
                <div className="mx-auto grid max-w-md grid-cols-5 items-end">
                    <NavigationSlot item={items.home} />
                    <NavigationSlot item={items.secondary} />

                    <button
                        type="button"
                        onClick={() => setActionsOpen(true)}
                        className="mx-auto -mt-8 flex h-14 w-14 items-center justify-center rounded-full bg-yale-blue text-white shadow-lg transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
                        aria-label="Otvori brze akcije i funkcionalnosti"
                        aria-expanded={actionsOpen}
                    >
                        <Plus className="h-7 w-7" />
                    </button>

                    <NavigationSlot item={items.requisitions} />
                    <NavigationSlot item={items.quickAction} />
                </div>
            </nav>

            {actionsOpen && (
                <MobileQuickActionMenu onClose={closeActions} />
            )}
        </>
    );
}

function selectBottomNavigationItems(
    primaryNavigation: NavigationItem[],
    quickActions: QuickAction[],
) {
    const itemAt = (position: MobileNavigationPosition) =>
        primaryNavigation.find((item) => item.mobilePosition === position);
    const home = itemAt(1);
    const configuredSecondary = itemAt(2);
    const configuredRequisitions = itemAt(4);
    const configuredRoutes = new Set(
        [home, configuredSecondary, configuredRequisitions]
            .filter((item): item is NavigationItem => item !== undefined)
            .map((item) => item.route),
    );
    const eligibleQuickActions = quickActions.filter(
        (action) => !configuredRoutes.has(action.route),
    );
    const preferredQuickAction = eligibleQuickActions.find((action) => action.primary)
        ?? eligibleQuickActions[0];
    const fallbackActions = eligibleQuickActions.filter(
        (action) => action !== preferredQuickAction,
    );
    let fallbackIndex = 0;
    const nextFallback = (slot: string) => {
        const action = fallbackActions[fallbackIndex++];
        return action ? toNavigationItem(action, slot) : undefined;
    };

    return {
        home,
        secondary: configuredSecondary ?? nextFallback("secondary"),
        requisitions: configuredRequisitions ?? nextFallback("requisitions"),
        quickAction: preferredQuickAction
            ? toNavigationItem(preferredQuickAction, "quick-action")
            : nextFallback("quick-action"),
    };
}

function toNavigationItem(action: QuickAction, slot: string): NavigationItem {
    return {
        id: `mobile-${slot}-${action.route}`,
        label: action.label,
        route: action.route,
        icon: action.icon,
        permission: action.permission,
        roles: action.roles,
    };
}

function NavigationSlot({ item }: { item: NavigationItem | undefined }) {
    return item ? <MobileNavItem item={item} /> : null;
}
