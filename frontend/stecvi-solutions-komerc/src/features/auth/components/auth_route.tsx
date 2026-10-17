import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAuth } from "../hooks/useAuth";

function LoadingScreen() {
    return (
        <main className="flex min-h-screen items-center justify-center bg-dust-grey/30">
            <p className="font-semibold text-yale-blue">Učitavanje...</p>
        </main>
    );
}

export function ProtectedRoute() {
    const { isAuthenticated, isLoading } = useAuth();
    const location = useLocation();

    if (isLoading) return <LoadingScreen />;
    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
    }
    return <Outlet />;
}

export function GuestRoute() {
    const { isAuthenticated, isLoading } = useAuth();

    if (isLoading) return <LoadingScreen />;
    return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}
