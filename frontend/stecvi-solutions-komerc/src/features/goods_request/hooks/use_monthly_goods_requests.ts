import { useCallback, useEffect, useMemo, useState } from "react";

import { goodsRequestApi } from "../goods_request_api";
import type { GoodsRequestRecord } from "../types/goods_request.types";

type RequestSnapshot = {
    key: string;
    records: GoodsRequestRecord[];
    error: string;
};

export function useMonthlyGoodsRequests(enabled: boolean, sessionKey: string) {
    const [selectedDate, setSelectedDate] = useState(() => {
        const now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), 1);
    });
    const [reloadVersion, setReloadVersion] = useState(0);
    const [snapshot, setSnapshot] = useState<RequestSnapshot>({
        key: "",
        records: [],
        error: "",
    });
    const requestKey = `${sessionKey}:${selectedDate.getFullYear()}-${selectedDate.getMonth()}:${reloadVersion}`;

    useEffect(() => {
        if (!enabled || !sessionKey) return;

        const controller = new AbortController();
        let active = true;
        goodsRequestApi.getMonthlyGoodsRequests(
            selectedDate.getFullYear(),
            selectedDate.getMonth() + 1,
            controller.signal,
        )
            .then((records) => {
                if (active) setSnapshot({ key: requestKey, records, error: "" });
            })
            .catch((requestError: unknown) => {
                if (requestError instanceof DOMException && requestError.name === "AbortError") return;
                if (!active) return;
                setSnapshot({
                    key: requestKey,
                    records: [],
                    error: requestError instanceof Error
                        ? requestError.message
                        : "Zahtevi za robu nisu učitani.",
                });
            });

        return () => {
            active = false;
            controller.abort();
        };
    }, [enabled, requestKey, selectedDate, sessionKey]);

    const reload = useCallback(() => setReloadVersion((version) => version + 1), []);
    const currentSnapshot = snapshot.key === requestKey ? snapshot : null;

    return useMemo(() => ({
        selectedDate,
        setSelectedDate,
        records: enabled ? currentSnapshot?.records ?? [] : [],
        loading: enabled && currentSnapshot === null,
        error: enabled ? currentSnapshot?.error ?? "" : "",
        reload,
    }), [currentSnapshot, enabled, reload, selectedDate]);
}
