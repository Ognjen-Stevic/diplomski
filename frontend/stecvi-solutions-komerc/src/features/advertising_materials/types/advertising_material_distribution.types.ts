export type AdvertisingMaterialDistributionItem = {
    materialId: number;
    quantity: number;
};

export type AdvertisingMaterialDistribution = {
    id: number;
    salesRep: {
        id: number;
        firstName: string;
        lastName: string;
    };
    customer: {
        id: number;
        objectName: string;
        companyName: string;
    };
    materialName: string;
    quantity: number;
    note: string | null;
    distributedAt: string;
};

export type CreateAdvertisingMaterialDistributionRequest = {
    customerId: number;
    items: AdvertisingMaterialDistributionItem[];
    note: string | null;
};
