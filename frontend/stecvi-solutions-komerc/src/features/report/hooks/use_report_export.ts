import { useCallback, useEffect, useRef, useState } from "react";

export function useReportExport(
    identity: object,
    download: (signal: AbortSignal) => Promise<void>,
) {
    const active = useRef<AbortController | null>(null);
    const [state, setState] = useState({ identity, exporting: false, error: "" });

    useEffect(() => () => {
        active.current?.abort();
        active.current = null;
    }, [identity]);

    const exportExcel = useCallback(async () => {
        if (active.current) return;
        const controller = new AbortController();
        active.current = controller;
        setState({ identity, exporting: true, error: "" });
        try {
            await download(controller.signal);
            if (!controller.signal.aborted) setState({ identity, exporting: false, error: "" });
        } catch (reason) {
            if (!controller.signal.aborted) {
                setState({ identity, exporting: false,
                    error: reason instanceof Error ? reason.message : "Excel izveštaj nije preuzet." });
            }
        } finally {
            if (active.current === controller) active.current = null;
        }
    }, [download, identity]);

    return {
        exporting: state.identity === identity && state.exporting,
        exportError: state.identity === identity ? state.error : "",
        exportExcel,
    };
}
