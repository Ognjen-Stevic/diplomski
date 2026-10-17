export type EmployeeRole = "SALES_REP" | "MANAGER" | "BOOKKEEPER";
export type CreatableEmployeeRole = EmployeeRole;

export type EmployeeManager = {
    id: number;
    firstName: string;
    lastName: string;
};

export type Employee = {
    id: number;
    firstName: string;
    lastName: string;
    email: string;
    phone: string | null;
    role: EmployeeRole;
    active: boolean;
    manager: EmployeeManager | null;
};

export type EmployeeCreatePayload = {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    phone: string | null;
    role: CreatableEmployeeRole;
    managerId: number | null;
};

export type EmployeeUpdatePayload = Omit<EmployeeCreatePayload, "password" | "role">;
