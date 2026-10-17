import { authenticatedFetch } from "../../app/api/http_client";
import type {
    Employee,
    EmployeeCreatePayload,
    EmployeeManager,
    EmployeeUpdatePayload,
} from "./types/employee.types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await authenticatedFetch(path, {
        ...init,
        headers: {
            ...(init?.body ? { "Content-Type": "application/json" } : {}),
            ...init?.headers,
        },
    });

    if (!response.ok) {
        const fallback = "Zahtev nije uspeo.";
        let message = fallback;
        try {
            const body = await response.json() as { message?: string; detail?: string };
            const reason = body.message ?? body.detail;
            message = reason ? `${fallback} ${reason}` : fallback;
        } catch {
            // Keep the fallback for non-JSON responses.
        }
        throw new Error(message);
    }
    return response.json() as Promise<T>;
}

export const employeesApi = {
    findAll: () => request<Employee[]>("/api/employees"),
    findActiveManagers: () => request<EmployeeManager[]>("/api/employees/manager-options"),
    create: (payload: EmployeeCreatePayload) => request<Employee>("/api/employees", {
        method: "POST",
        body: JSON.stringify(payload),
    }),
    update: (employeeId: number, payload: EmployeeUpdatePayload) => request<Employee>(
        `/api/employees/${employeeId}`,
        { method: "PATCH", body: JSON.stringify(payload) },
    ),
    setActive: (employeeId: number, active: boolean) => request<Employee>(
        `/api/employees/${employeeId}/active`,
        { method: "PATCH", body: JSON.stringify({ active }) },
    ),
};
