import { ChevronLeft, ChevronRight } from "lucide-react";

export function Pagination({
    currentPage,
    totalPages,
    onChange,
}: {
    currentPage: number;
    totalPages: number;
    onChange: (page: number) => void;
}) {
    if (totalPages <= 1) return null;

    const pageNumbers = Array.from(
        { length: totalPages },
        (_, index) => index + 1,
    ).filter(
        (page) =>
            page === 1 || page === totalPages || Math.abs(page - currentPage) <= 1,
    );

    return (
        <nav
            className="mt-7 flex items-center justify-between gap-3"
            aria-label="Paginacija kupaca"
        >
            <button
                type="button"
                disabled={currentPage === 1}
                onClick={() => onChange(currentPage - 1)}
                className="inline-flex items-center gap-2 rounded-xl border border-dust-grey bg-white px-3 py-2.5 text-sm font-semibold text-yale-blue transition hover:border-stormy-teal disabled:cursor-not-allowed disabled:opacity-40"
            >
                <ChevronLeft className="h-4 w-4" />
                <span className="hidden sm:inline">Prethodna</span>
            </button>

            <div className="flex items-center gap-1">
                {pageNumbers.map((page, index) => {
                    const previousPage = pageNumbers[index - 1];
                    const showEllipsis = previousPage && page - previousPage > 1;

                    return (
                        <div key={page} className="flex items-center gap-1">
                            {showEllipsis && (
                                <span className="px-1 text-sm text-graphite/45">…</span>
                            )}
                            <button
                                type="button"
                                onClick={() => onChange(page)}
                                aria-current={page === currentPage ? "page" : undefined}
                                className={[
                                    "h-10 min-w-10 rounded-xl px-3 text-sm font-semibold transition",
                                    page === currentPage
                                        ? "bg-yale-blue text-white"
                                        : "text-graphite hover:bg-dust-grey/35",
                                ].join(" ")}
                            >
                                {page}
                            </button>
                        </div>
                    );
                })}
            </div>

            <button
                type="button"
                disabled={currentPage === totalPages}
                onClick={() => onChange(currentPage + 1)}
                className="inline-flex items-center gap-2 rounded-xl border border-dust-grey bg-white px-3 py-2.5 text-sm font-semibold text-yale-blue transition hover:border-stormy-teal disabled:cursor-not-allowed disabled:opacity-40"
            >
                <span className="hidden sm:inline">Sledeća</span>
                <ChevronRight className="h-4 w-4" />
            </button>
        </nav>
    );
}