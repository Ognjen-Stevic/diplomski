export type ManagedProduct = {
    id: number;
    name: string;
    code: string;
    pictureUrl: string;
    price: number;
    volumeLiters: number;
    stimulationPerUnit: number;
    managerStimulationPerUnit: number;
    active: boolean;
};

export type ProductCreatePayload = Omit<ManagedProduct, "id" | "active" | "pictureUrl">;

export type ProductStatusFilter = "ALL" | "ACTIVE" | "INACTIVE";

export type ProductPage = {
    content: ManagedProduct[];
    totalElements: number;
    totalPages: number;
    pageNumber: number;
};
