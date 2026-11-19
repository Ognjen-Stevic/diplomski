import { ChevronLeft, ChevronRight } from "lucide-react";

type PaginationProps = {
  currentPage: number;
  totalPages: number;
  onChange: (page: number) => void;
};

export function Pagination({
  currentPage,
  totalPages,
  onChange,
}: PaginationProps) {
  if (totalPages <= 1) return null;

  const pageNumbers = Array.from(
    { length: totalPages },
    (_, index) => index + 1,
  ).filter(
    (page) =>
      page === 1 || page === totalPages || Math.abs(page - currentPage) <= 1,
  );

  const navigationButtonClass =
    "group inline-flex min-w-10 items-center justify-center gap-2 rounded-xl border border-stormy-teal/30 bg-white px-3 py-2.5 text-sm font-semibold text-yale-blue shadow-sm transition hover:-translate-y-0.5 hover:border-stormy-teal hover:bg-stormy-teal/5 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:cursor-not-allowed disabled:border-dust-grey disabled:bg-slate-50 disabled:text-graphite/35 disabled:shadow-none disabled:hover:translate-y-0 sm:min-w-32";

  return (
    <nav
      className="mt-7 flex w-full items-center justify-center gap-2 sm:gap-3"
      aria-label="Paginacija"
    >
      <button
        type="button"
        disabled={currentPage === 1}
        onClick={() => onChange(currentPage - 1)}
        className={navigationButtonClass}
        aria-label="Prethodna stranica"
      >
        <ChevronLeft
          className="h-4 w-4 transition-transform group-hover:-translate-x-0.5 group-disabled:translate-x-0"
          aria-hidden="true"
        />
        <span className="hidden sm:inline">Prethodna</span>
      </button>

      <div className="flex items-center gap-1" aria-label="Stranice">
        {pageNumbers.map((page, index) => {
          const previousPage = pageNumbers[index - 1];
          const showEllipsis = previousPage && page - previousPage > 1;

          return (
            <div key={page} className="flex items-center gap-1">
              {showEllipsis && (
                <span className="px-1 text-sm text-graphite/45" aria-hidden="true">
                  …
                </span>
              )}
              <button
                type="button"
                onClick={() => onChange(page)}
                aria-label={`Stranica ${page}`}
                aria-current={page === currentPage ? "page" : undefined}
                className={[
                  "h-10 min-w-10 rounded-xl px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-stormy-teal/30",
                  page === currentPage
                    ? "bg-yale-blue text-white shadow-sm"
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
        className={navigationButtonClass}
        aria-label="Sledeća stranica"
      >
        <span className="hidden sm:inline">Sledeća</span>
        <ChevronRight
          className="h-4 w-4 transition-transform group-hover:translate-x-0.5 group-disabled:translate-x-0"
          aria-hidden="true"
        />
      </button>
    </nav>
  );
}
