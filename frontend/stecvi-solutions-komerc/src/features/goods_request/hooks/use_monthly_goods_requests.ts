import { useCallback, useState } from "react";

import { goodsRequestApi } from "../goods_request_api";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";

export function useMonthlyGoodsRequests(enabled: boolean, sessionKey: string) {
    const [selectedDate, setSelectedDate] = useState(() => {
        const now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), 1);
    });
    const year = selectedDate.getFullYear(); const month = selectedDate.getMonth() + 1;
    const load = useCallback((signal: AbortSignal) => goodsRequestApi.getMonthlyGoodsRequests(year, month, signal), [year, month]);
    const query = useSessionRequest(`${sessionKey}:${year}:${month}`, load, enabled);
    return { selectedDate, setSelectedDate, records: query.data ?? [], loading: query.loading, error: query.error, reload: query.reload, isCurrent: query.isCurrent, context: query.context };
}
