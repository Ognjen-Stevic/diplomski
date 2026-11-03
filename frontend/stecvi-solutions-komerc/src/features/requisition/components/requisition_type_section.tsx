import { GRATIS_REASONS, GRATIS_REASON_LABELS } from "../data/requisition.data";
import type { GratisReason, RequisitionType } from "../types/requisition.types";

type RequisitionTypeSectionProps = {
    requisitionType: RequisitionType;
    gratisReason: GratisReason;
    customReason: string;
    submitted: boolean;
    typeDisabled?: boolean;
    onTypeChange: (type: RequisitionType) => void;
    onReasonChange: (reason: GratisReason) => void;
    onCustomReasonChange: (value: string) => void;
};

export function RequisitionTypeSection({
    requisitionType,
    gratisReason,
    customReason,
    submitted,
    typeDisabled = false,
    onTypeChange,
    onReasonChange,
    onCustomReasonChange,
}: RequisitionTypeSectionProps) {
    const showReasonError = submitted && requisitionType === "gratis" && !gratisReason;
    const showCustomReasonError = submitted
        && requisitionType === "gratis"
        && gratisReason === "OTHER"
        && !customReason.trim();

    return (
        <section className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm sm:p-6">
            <fieldset>
                <legend className="text-base font-bold text-yale-blue">Vrsta trebovanja</legend>
                <div className="mt-3 grid max-w-md grid-cols-2 rounded-xl bg-dust-grey/35 p-1">
                    {([[
                        "classic",
                        "Klasično",
                    ], [
                        "gratis",
                        "Gratis",
                    ]] as const).map(([value, label]) => (
                        <label
                            key={value}
                            className={`rounded-lg px-4 py-2.5 text-center text-sm font-semibold transition ${typeDisabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"} ${
                                requisitionType === value
                                    ? "bg-white text-yale-blue shadow-sm ring-1 ring-graphite/10"
                                    : "text-graphite/60 hover:text-graphite"
                            }`}
                        >
                            <input
                                type="radio"
                                name="requisitionType"
                                value={value}
                                checked={requisitionType === value}
                                disabled={typeDisabled}
                                onChange={() => onTypeChange(value)}
                                className="sr-only"
                            />
                            {label}
                        </label>
                    ))}
                </div>
            </fieldset>

            {requisitionType === "gratis" && (
                <fieldset className="mt-5 border-t border-graphite/10 pt-5">
                    <legend className="text-sm font-bold text-graphite">
                        Razlog <span className="text-red-600">*</span>
                    </legend>
                    <div className="mt-3 flex flex-wrap gap-2">
                        {GRATIS_REASONS.map((reason) => (
                            <label
                                key={reason}
                                className={`cursor-pointer rounded-xl border px-4 py-2.5 text-sm font-semibold transition ${
                                    gratisReason === reason
                                        ? "border-stormy-teal bg-stormy-teal/10 text-yale-blue"
                                        : "border-dust-grey text-graphite/70 hover:border-stormy-teal/60"
                                }`}
                            >
                                <input
                                    type="radio"
                                    name="gratisReason"
                                    value={reason}
                                    checked={gratisReason === reason}
                                    onChange={() => onReasonChange(reason)}
                                    className="sr-only"
                                />
                                {GRATIS_REASON_LABELS[reason]}
                            </label>
                        ))}
                    </div>
                    {showReasonError && (
                        <p role="alert" className="mt-2 text-sm font-medium text-red-700">
                            Izaberite razlog za gratis trebovanje.
                        </p>
                    )}

                    {gratisReason === "OTHER" && (
                        <label className="mt-4 block max-w-xl text-sm font-semibold text-graphite">
                            Drugi razlog <span className="text-red-600">*</span>
                            <input
                                type="text"
                                value={customReason}
                                onChange={(event) => onCustomReasonChange(event.target.value)}
                                maxLength={200}
                                aria-invalid={showCustomReasonError}
                                aria-describedby={showCustomReasonError ? "custom-reason-error" : undefined}
                                placeholder="Unesite razlog"
                                className={`mt-2 w-full rounded-xl border bg-white px-4 py-3 font-normal text-graphite outline-none transition placeholder:text-graphite/40 focus:ring-2 ${
                                    showCustomReasonError
                                        ? "border-red-500 focus:border-red-500 focus:ring-red-100"
                                        : "border-dust-grey focus:border-stormy-teal focus:ring-stormy-teal/20"
                                }`}
                            />
                            {showCustomReasonError && (
                                <span id="custom-reason-error" role="alert" className="mt-2 block font-medium text-red-700">
                                    Unesite razlog za gratis trebovanje.
                                </span>
                            )}
                        </label>
                    )}
                </fieldset>
            )}
        </section>
    );
}
