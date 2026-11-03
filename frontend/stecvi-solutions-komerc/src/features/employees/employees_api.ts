import { apiRequest } from "../../app/api/http_client";
import type {
    Employee,
    EmployeeCreatePayload,
    EmployeeManager,
    EmployeeUpdatePayload,
} from "./types/employee.types";

export const employeesApi = {
    findAll: (signal?: AbortSignal) => apiRequest<Employee[]>("/api/employees", { signal }),
    findActiveManagers: (signal?: AbortSignal) => apiRequest<EmployeeManager[]>("/api/employees/manager-options", { signal }),
    create: (payload: EmployeeCreatePayload) => apiRequest<Employee>("/api/employees", {
        method: "POST",
        body: JSON.stringify(payload),
    }),
    update: (employeeId: number, payload: EmployeeUpdatePayload) => apiRequest<Employee>(
        `/api/employees/${employeeId}`,
        { method: "PATCH", body: JSON.stringify(payload) },
    ),
    setActive: (employeeId: number, active: boolean) => apiRequest<Employee>(
        `/api/employees/${employeeId}/active`,
        { method: "PATCH", body: JSON.stringify({ active }) },
    ),
};
