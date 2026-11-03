import { Component, type ReactNode } from "react";
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
    state = { failed: false };
    static getDerivedStateFromError() { return { failed: true }; }
    render() {
        if (this.state.failed) return <main className="p-6 text-graphite"><h1 className="text-2xl font-bold">Došlo je do neočekivane greške.</h1><p>Pokušajte ponovo da učitate stranicu.</p><button onClick={() => window.location.reload()}>Pokušaj ponovo</button><a className="ml-4" href="/">Početna</a></main>;
        return this.props.children;
    }
}
