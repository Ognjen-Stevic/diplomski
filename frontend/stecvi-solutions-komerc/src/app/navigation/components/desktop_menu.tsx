import type { NavigationItem } from "../types/navigation.types";
import { DesktopMenuItem } from "./desktop_menu_item";

type DesktopMenuProps = {
    navigation: NavigationItem[];
};

export default function DesktopMenu({ navigation }: DesktopMenuProps) {
    return (
        <aside className="hidden min-h-[calc(100vh-4rem)] border-r border-dust-grey bg-white p-5 lg:block">
            <nav className="sticky top-20 space-y-2" aria-label="Glavna navigacija">
                {navigation.map((item) => (
                    <DesktopMenuItem key={item.id} item={item} />
                ))}
            </nav>
        </aside>
    );
}
