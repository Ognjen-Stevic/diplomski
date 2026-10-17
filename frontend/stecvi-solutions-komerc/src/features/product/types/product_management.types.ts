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
