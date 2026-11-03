import { Fragment, useState, type ReactNode } from "react";
import { useAuth } from "../../features/auth/hooks/useAuth";
// Unsaved forms and dialog selections must not survive a change of authenticated session.
export function SessionScope({ children }: { children: ReactNode }) {
    const { sessionId } = useAuth();
    const [state, setState] = useState({ sessionId, generation: 0 });
    if (state.sessionId !== sessionId) setState({ sessionId, generation: state.generation + 1 });
    return <Fragment key={state.generation}>{children}</Fragment>;
}
