import { render, screen } from "@testing-library/react";
import { Home, UserPlus, UsersRound } from "lucide-react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";

import type { NavigationItem } from "../app/navigation/types/navigation.types";

vi.mock("../features/dashboard/hooks/use_visible_quick_actions", () => ({
    useVisibleQuickActions: () => [{
        label: "Zaposli radnika",
        description: "Dodajte novog zaposlenog u sistem.",
        route: "/zaposleni?action=new",
        icon: UserPlus,
        roles: ["CEO"],
        primary: true,
    }],
}));

import { MobileBottomNavigation } from "../app/navigation/components/mobile_bottom_navigation";

const navigation: NavigationItem[] = [
    {
        id: "home",
        label: "Početna",
        route: "/",
        icon: Home,
        mobilePosition: 1,
    },
    {
        id: "team",
        label: "Moj tim",
        route: "/team",
        icon: UsersRound,
        roles: ["CEO"],
    },
];

it("uses Moj tim instead of Zaposli radnika as the CEO mobile shortcut", () => {
    render(
        <MemoryRouter>
            <MobileBottomNavigation navigation={navigation} />
        </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "Moj tim" })).toHaveAttribute("href", "/team");
    expect(screen.queryByRole("link", { name: "Zaposli radnika" })).not.toBeInTheDocument();
});
