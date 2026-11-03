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
    const { isAuthenticated, isLoading, restorationError, retrySession, logout } = useAuth();
    const location = useLocation();

    if (restorationError) return <main className="p-6"><p role="alert">{restorationError}</p><button onClick={retrySession}>Pokušaj ponovo</button><button onClick={logout}>Odjavi se</button></main>;
    if (isLoading) return <LoadingScreen />;
    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
    }
    return <Outlet />;
}

export function GuestRoute() {
    const { isAuthenticated, isLoading, restorationError, retrySession, logout } = useAuth();

    if (restorationError) return <main className="p-6"><p role="alert">{restorationError}</p><button onClick={retrySession}>Pokušaj ponovo</button><button onClick={logout}>Odjavi se</button></main>;
    if (isLoading) return <LoadingScreen />;
    return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}
