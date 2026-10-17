import type { RequestCatalogItem } from "./request_catalog_item";

export type Product = RequestCatalogItem & {
    id?: number;
    code: string;
    price: number;
    pictureUrl: string;
};
