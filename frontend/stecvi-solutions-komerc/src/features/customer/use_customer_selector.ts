import { useEffect, useState } from "react";

import { customerApi } from "./customer_api";
import type { Customer } from "./types/customer.types";

export function useCustomerSelector(customerId: number | null) {
    const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
    const [customerSearch, setCustomerSearch] = useState("");
    const [matchingCustomers, setMatchingCustomers] = useState<Customer[]>([]);
    const [isPreselected, setIsPreselected] = useState(false);

    useEffect(() => {
        if (customerId === null) return;
        let active = true;
        customerApi.findById(customerId).then((customer) => {
            if (active) {
                setSelectedCustomer(customer);
                setIsPreselected(true);
            }
        }).catch(() => undefined);
        return () => { active = false; };
    }, [customerId]);

    useEffect(() => {
        if (selectedCustomer || !customerSearch.trim()) {
            setMatchingCustomers([]);
            return;
        }
        let active = true;
        const timeout = window.setTimeout(() => {
            customerApi.findAll(customerSearch, 0, 6)
                .then((result) => { if (active) setMatchingCustomers(result.content); })
                .catch(() => { if (active) setMatchingCustomers([]); });
        }, 250);
        return () => { active = false; window.clearTimeout(timeout); };
    }, [customerSearch, selectedCustomer]);

    return { selectedCustomer, setSelectedCustomer, customerSearch, setCustomerSearch, matchingCustomers, isPreselected };
}
