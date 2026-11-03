import { useCallback, useMemo, useState } from "react";
import { useSessionRequest } from "../../shared/hooks/use_session_request";
import { customerApi } from "./customer_api";
import type { Customer } from "./types/customer.types";

export function useCustomerSelector(customerId: number | null) {
  const preselect = useCallback(
    (signal: AbortSignal) => customerApi.findById(customerId!, signal),
    [customerId],
  );
  const selected = useSessionRequest(
    `customer:${customerId}`,
    preselect,
    customerId !== null,
  );
  const [choice, setChoice] = useState<{
    context: object;
    customer: Customer | null;
  } | null>(null);
  const [searchState, setSearchState] = useState<{
    context: object;
    text: string;
  } | null>(null);
  const customerSearch =
    searchState?.context === selected.context ? searchState.text : "";
  const selectedCustomer =
    choice?.context === selected.context ? choice.customer : selected.data;
  const search = useCallback(
    (signal: AbortSignal) =>
      new Promise<Awaited<ReturnType<typeof customerApi.findAll>>>(
        (resolve, reject) => {
          const timer = window.setTimeout(() => {
            void customerApi
              .findAll(customerSearch, 0, 6, signal)
              .then(resolve, reject);
          }, 250);
          signal.addEventListener(
            "abort",
            () => {
              clearTimeout(timer);
              reject(new DOMException("Aborted", "AbortError"));
            },
            { once: true },
          );
        },
      ),
    [customerSearch],
  );
  const results = useSessionRequest(
    `search:${customerId}:${customerSearch.trim()}`,
    search,
    !selectedCustomer && Boolean(customerSearch.trim()),
  );
  return useMemo(
    () => ({
      selectedCustomer,
      customerSearch,
      matchingCustomers: results.data?.content ?? [],
      isPreselected:
        customerId !== null &&
        choice?.context !== selected.context &&
        selected.data !== null,
      loading: selected.loading || results.loading,
      error: (choice?.context === selected.context ? "" : selected.error) || results.error,
      setSelectedCustomer: (customer: Customer | null) =>
        setChoice({ context: selected.context, customer }),
      setCustomerSearch: (text: string) =>
        setSearchState({ context: selected.context, text }),
    }),
    [
      selectedCustomer,
      customerSearch,
      results.data,
      results.loading,
      results.error,
      selected.loading,
      selected.error,
      selected.context,
      selected.data,
      customerId,
      choice,
    ],
  );
}
