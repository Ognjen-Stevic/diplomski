import { Download, LoaderCircle } from "lucide-react";

type ReportExportButtonProps = {
    exporting: boolean;
    disabled?: boolean;
    onClick: () => void;
};

export function ReportExportButton({ exporting, disabled = false, onClick }: ReportExportButtonProps) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled || exporting}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-yale-blue bg-white px-4 py-2.5 text-sm font-bold text-yale-blue transition hover:bg-yale-blue hover:text-white focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:cursor-not-allowed disabled:opacity-55"
        >
            {exporting
                ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
                : <Download className="h-4 w-4" aria-hidden="true" />}
            {exporting ? "Preuzimanje..." : "Export Excel"}
        </button>
    );
}
