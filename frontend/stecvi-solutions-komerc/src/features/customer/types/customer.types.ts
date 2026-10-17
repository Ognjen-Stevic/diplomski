export type CustomerStatus = "draft" | "approved" | "deactivated" | "deleted";

export type SalesRepresentativeSummary = {
    id: number;
    firstName: string;
    lastName: string;
};

export type Customer = {
    id: number;
    salesRep: SalesRepresentativeSummary | null;
    actions: {
        canCreateRequisition: boolean;
        canViewRequisitionHistory: boolean;
        canChangeSalesRep: boolean;
    };
    objectName: string;
    companyName: string;
    pib: string;
    phone: string;
    email: string;
    address: string;
    googleMapsUrl: string;
    note: string;
    status: CustomerStatus;
};

export type CustomerPayload = {
    objectName: string;
    companyName: string;
    pib: string;
    phone: string | null;
    email: string | null;
    address: string;
    googleMapsUrl: string | null;
    note: string | null;
};
