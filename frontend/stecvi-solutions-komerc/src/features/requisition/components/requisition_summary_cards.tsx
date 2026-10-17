import { ClipboardList, Gift } from "lucide-react";

type RequisitionSummaryCardsProps = {
    requisitionCount: number;
    gratisCount: number;
};

export function RequisitionSummaryCards({ requisitionCount, gratisCount }: RequisitionSummaryCardsProps) {
    return (
        <section className="mt-4 grid gap-4 sm:grid-cols-2">
            <article className="flex items-center gap-4 rounded-2xl border border-dust-grey bg-white p-5 shadow-sm">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-yale-blue/10 text-yale-blue">
                    <ClipboardList className="h-5 w-5" />
                </span>
                <div>
                    <p className="text-2xl font-bold text-yale-blue">{requisitionCount}</p>
                    <p className="text-sm text-graphite/55">Trebovanja u periodu</p>
                </div>
            </article>

            <article className="flex items-center gap-4 rounded-2xl border border-dust-grey bg-white p-5 shadow-sm">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-stormy-teal/10 text-stormy-teal">
                    <Gift className="h-5 w-5" />
                </span>
                <div>
                    <p className="text-2xl font-bold text-yale-blue">{gratisCount}</p>
                    <p className="text-sm text-graphite/55">Gratis trebovanja</p>
                </div>
            </article>
        </section>
    );
}
