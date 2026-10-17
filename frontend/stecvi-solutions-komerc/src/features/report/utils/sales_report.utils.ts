export const formatInteger = (value: number) => new Intl.NumberFormat("sr-Latn-RS", {
    maximumFractionDigits: 0,
}).format(value);

export const formatLiters = (value: number) => `${new Intl.NumberFormat("sr-Latn-RS", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 3,
}).format(value)} l`;

export const formatRsd = (value: number) => new Intl.NumberFormat("sr-Latn-RS", {
    style: "currency",
    currency: "RSD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
}).format(value);
