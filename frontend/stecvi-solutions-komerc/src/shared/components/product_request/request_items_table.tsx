import type { RequestCatalogItem } from "../../types/request_catalog_item";
import { Minus, Plus } from "lucide-react";

type RequestItemsTableProps<T extends RequestCatalogItem> = {
    products: readonly T[];
    title: string;
    description: string;
    quantities: string[];
    quantityErrors: string[];
    touchedQuantities: boolean[];
    submitted: boolean;
    hasSelectedProduct: boolean;
    quantitySteppers?: boolean;
    visibleProductKeys?: ReadonlySet<string>;
    onQuantityChange: (index: number, value: string) => void;
    onQuantityBlur: (index: number) => void;
};

export function RequestItemsTable<T extends RequestCatalogItem>({
    products,
    title,
    description,
    quantities,
    quantityErrors,
    touchedQuantities,
    submitted,
    hasSelectedProduct,
    quantitySteppers = false,
    visibleProductKeys,
    onQuantityChange,
    onQuantityBlur,
}: RequestItemsTableProps<T>) {
    return (
        <section className="overflow-hidden rounded-2xl border border-dust-grey bg-white shadow-sm">
            <div className="flex items-center justify-between gap-4 border-b border-dust-grey px-5 py-4 sm:px-6">
                <div>
                    <h2 className="text-lg font-bold text-yale-blue">{title}</h2>
                    <p className="mt-0.5 text-sm text-graphite/55">{description}</p>
                </div>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                    <thead>
                        <tr className="bg-yale-blue/[0.04] text-xs uppercase tracking-wider text-graphite/55">
                            <th scope="col" className="px-5 py-3 font-bold sm:px-6">Proizvod</th>
                            <th scope="col" className="w-40 px-5 py-3 text-right font-bold sm:px-6">Količina</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-dust-grey/75">
                        {products.map((product, index) => {
                            if (visibleProductKeys && !visibleProductKeys.has(product.key)) return null;
                            const showError = (submitted || touchedQuantities[index]) && Boolean(quantityErrors[index]);
                            const minimumQuantity = product.active === false ? 1 : 0;
                            const numericQuantity = Number(quantities[index]);
                            const stepQuantity = (change: number) => {
                                const currentQuantity = Number.isSafeInteger(numericQuantity)
                                    ? numericQuantity
                                    : minimumQuantity;
                                onQuantityChange(index, String(Math.max(minimumQuantity, currentQuantity + change)));
                            };
                            return (
                                <tr key={product.key} className="transition hover:bg-yale-blue/[0.025]">
                                    <th scope="row" className="px-5 py-3 text-sm font-medium text-graphite sm:px-6">
                                        <span className="flex items-center gap-3">
                                            {product.imageUrl && (
                                                <img
                                                    src={product.imageUrl}
                                                    alt=""
                                                    className="h-12 w-12 shrink-0 rounded-xl border border-dust-grey object-cover"
                                                />
                                            )}
                                            <span>
                                                <span className="block">{product.name}</span>
                                                {product.active === false && (
                                                    <span className="mt-1 inline-flex rounded-full bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-800 ring-1 ring-inset ring-amber-200">
                                                        Neaktivan artikal
                                                    </span>
                                                )}
                                                {product.details && (
                                                    <span className="mt-0.5 block text-xs font-normal text-graphite/55">
                                                        {product.details}
                                                    </span>
                                                )}
                                            </span>
                                        </span>
                                    </th>
                                    <td className="px-5 py-2.5 sm:px-6">
                                        <div className={quantitySteppers ? "ml-auto w-36" : "ml-auto w-28"}>
                                            <div className={quantitySteppers ? "flex items-center justify-end gap-2" : undefined}>
                                                {quantitySteppers && (
                                                    <button
                                                        type="button"
                                                        onClick={() => stepQuantity(-1)}
                                                        disabled={Number.isSafeInteger(numericQuantity) && numericQuantity <= minimumQuantity}
                                                        aria-label={`Smanji količinu za ${product.name}`}
                                                        className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-dust-grey bg-white text-yale-blue transition hover:border-stormy-teal hover:bg-stormy-teal/10 disabled:cursor-not-allowed disabled:opacity-40"
                                                    >
                                                        <Minus className="h-4 w-4" />
                                                    </button>
                                                )}
                                                <input
                                                    type="number"
                                                    min={String(minimumQuantity)}
                                                    step="1"
                                                    inputMode="numeric"
                                                    value={quantities[index]}
                                                    onChange={(event) => onQuantityChange(index, event.target.value)}
                                                    onBlur={() => onQuantityBlur(index)}
                                                    aria-label={`Količina za ${product.name}`}
                                                    aria-invalid={showError}
                                                    className={`${quantitySteppers ? "h-9 w-12 px-1 text-center [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none" : "w-full px-3 text-right"} rounded-lg border py-2 font-semibold tabular-nums outline-none transition focus:ring-2 ${
                                                        showError
                                                            ? "border-red-500 text-red-800 focus:ring-red-100"
                                                            : "border-dust-grey focus:border-stormy-teal focus:ring-stormy-teal/20"
                                                    }`}
                                                />
                                                {quantitySteppers && (
                                                    <button
                                                        type="button"
                                                        onClick={() => stepQuantity(1)}
                                                        aria-label={`Povećaj količinu za ${product.name}`}
                                                        className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-dust-grey bg-white text-yale-blue transition hover:border-stormy-teal hover:bg-stormy-teal/10"
                                                    >
                                                        <Plus className="h-4 w-4" />
                                                    </button>
                                                )}
                                            </div>
                                            {showError && (
                                                <p role="alert" className="mt-1 text-right text-xs text-red-700">{quantityErrors[index]}</p>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                        {visibleProductKeys?.size === 0 && (
                            <tr>
                                <td colSpan={2} className="px-5 py-8 text-center text-sm text-graphite/55 sm:px-6">
                                    Nema proizvoda u izabranoj kategoriji.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            {submitted && !hasSelectedProduct && (
                <p role="alert" className="border-t border-red-200 bg-red-50 px-5 py-3 text-sm font-medium text-red-700 sm:px-6">
                    Unesite količinu veću od 0 za najmanje jednu stavku.
                </p>
            )}
        </section>
    );
}
