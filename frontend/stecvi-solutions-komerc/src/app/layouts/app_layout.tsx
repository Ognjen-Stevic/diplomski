import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

import { useAuth } from "../../features/auth/hooks/useAuth";
import { useApplicationNavigation } from "../navigation/hooks/use_application_navigation";
import Header from "./components/header";
import DesktopMenu from "../navigation/components/desktop_menu";
import { MobileBottomNavigation } from "../navigation/components/mobile_bottom_navigation";
import { APP_ROUTES } from "../routes";

type AppLayoutProps = {
    children: ReactNode;
};

export default function AppLayout({ children }: AppLayoutProps) {
    const auth = useAuth();
    const { navigation } = useApplicationNavigation();

    if (!auth.user) {
        return <Navigate to={APP_ROUTES.login} replace />;
    }

    return (
        <div className="min-h-screen bg-dust-grey/40 text-graphite">
            <Header
                name={auth.user.name}
                role={auth.user.role}
            />

            <div className="lg:grid lg:grid-cols-[250px_minmax(0,1fr)]">
                <DesktopMenu navigation={navigation} />

                <div className="min-w-0 w-full">
                    {children}
                </div>
            </div>

            <MobileBottomNavigation
                navigation={navigation}
            />
        </div>
    );
}
