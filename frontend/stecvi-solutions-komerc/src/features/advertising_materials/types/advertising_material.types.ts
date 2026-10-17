export type AdvertisingMaterial = {
    id: number;
    name: string;
    active: boolean;
};

export type AvailableAdvertisingMaterial = Pick<AdvertisingMaterial, "id" | "name">;
