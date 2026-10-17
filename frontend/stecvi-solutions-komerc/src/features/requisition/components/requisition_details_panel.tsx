import { useEffect, useRef, useState } from "react";
import { Download, FilePenLine, Mail, Package, X } from "lucide-react";

import { useAuth } from "../../auth/hooks/useAuth";
import type { RequisitionListItem, RequisitionStatus } from "../types/requisition.types";
import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";
import { requisitionApi } from "../requisition_api";
import { RequisitionStatusControl } from "./requisition_status_control";

const formatDate = (date: string) => new Intl.DateTimeFormat("sr-Latn-RS", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
}).format(new Date(date));

const GRATIS_REASON_LABELS = {
    LISTING: "Za ulistavanje",
    CONSUMPTION: "Za potrošnju",
    GIFT: "Poklon",
    OTHER: "Drugo",
} as const;

export function RequisitionDetailsPanel({ requisition, onClose, onEdit, onStatusChange }: {
    requisition: RequisitionListItem;
    onClose: () => void;
    onEdit: () => void;
    onStatusChange: (status: RequisitionStatus, note: string | null) => Promise<void>;
}) {
    const { user } = useAuth();
    const [downloadingPdf, setDownloadingPdf] = useState(false);
    const [pdfError, setPdfError] = useState("");
    const [emailModalOpen, setEmailModalOpen] = useState(false);
    const [emailRecipient, setEmailRecipient] = useState<"SALES_REP" | "CUSTOMER">("SALES_REP");
    const [sendingEmail, setSendingEmail] = useState(false);
    const [emailFeedback, setEmailFeedback] = useState<{ type: "success" | "error"; message: string } | null>(null);
    const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key !== "Escape") return;
            if (emailModalOpen) {
                if (!sendingEmail) {
                    setEmailModalOpen(false);
                    setEmailFeedback(null);
                }
                return;
            }
            onClose();
        };
        document.addEventListener("keydown", handleKeyDown);
        document.body.style.overflow = "hidden";
        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = "";
        };
    }, [emailModalOpen, onClose, sendingEmail]);

    useEffect(() => () => {
        if (closeTimer.current) clearTimeout(closeTimer.current);
    }, []);

    const closeEmailModal = () => {
        if (sendingEmail) return;
        if (closeTimer.current) clearTimeout(closeTimer.current);
        setEmailModalOpen(false);
        setEmailFeedback(null);
    };

    const sendEmail = async () => {
        setSendingEmail(true);
        setEmailFeedback(null);
        try {
            await requisitionApi.sendPdfByEmail(requisition.id, emailRecipient);
            setEmailFeedback({ type: "success", message: "E-mail je uspešno poslat." });
            closeTimer.current = setTimeout(() => {
                setEmailModalOpen(false);
                setEmailFeedback(null);
            }, 1400);
        } catch (error) {
            setEmailFeedback({
                type: "error",
                message: error instanceof Error ? error.message : "E-mail nije poslat.",
            });
            closeTimer.current = setTimeout(() => {
                setEmailModalOpen(false);
                setEmailFeedback(null);
            }, 2500);
        } finally {
            setSendingEmail(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex justify-end bg-graphite/45" role="dialog" aria-modal="true" aria-labelledby="requisition-details-title">
            <button type="button" aria-label="Zatvori detalje trebovanja" onClick={onClose} className="absolute inset-0 cursor-default" />
            <section className="relative z-10 flex h-full w-full max-w-xl flex-col overflow-hidden bg-white shadow-2xl">
                <header className="flex items-start justify-between gap-4 border-b border-dust-grey px-5 py-5 sm:px-7">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Trebovanje #{requisition.id}</p>
                        <h2 id="requisition-details-title" className="mt-1 text-2xl font-bold text-yale-blue">{requisition.buyerName}</h2>
                        <p className="mt-1 text-sm text-graphite/60">{requisition.companyName}</p>
                    </div>
                    <button type="button" onClick={onClose} aria-label="Zatvori" className="rounded-xl p-2 text-graphite transition hover:bg-dust-grey/35"><X className="h-5 w-5" /></button>
                </header>
                <div className="flex-1 overflow-y-auto px-5 py-6 sm:px-7">
                    <dl className="grid grid-cols-2 gap-4 rounded-2xl border border-dust-grey p-4">
                        <div><dt className="text-xs font-bold uppercase text-graphite/45">Vrsta</dt><dd className="mt-1 font-semibold text-graphite">{requisition.type === "gratis" ? "Gratis" : "Klasično"}</dd></div>
                        <div><dt className="text-xs font-bold uppercase text-graphite/45">Status</dt><dd className="mt-1 font-semibold text-graphite">{REQUISITION_STATUS_LABELS[requisition.status]}</dd></div>
                        <div className="col-span-2"><dt className="text-xs font-bold uppercase text-graphite/45">Datum</dt><dd className="mt-1 font-semibold text-graphite">{formatDate(requisition.createdAt)}</dd></div>
                        {requisition.gratisReason && <div className="col-span-2"><dt className="text-xs font-bold uppercase text-graphite/45">Gratis razlog</dt><dd className="mt-1 font-semibold text-graphite">{requisition.gratisReason === "OTHER" ? requisition.customReason : GRATIS_REASON_LABELS[requisition.gratisReason]}</dd></div>}
                    </dl>
                    {requisition.actions.canEdit && (
                        <button type="button" onClick={onEdit} className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-yale-blue px-4 py-3 font-semibold text-yale-blue transition hover:bg-yale-blue/5 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30">
                            <FilePenLine className="h-4 w-4" />
                            Izmeni trebovanje
                        </button>
                    )}
                    <button
                        type="button"
                        disabled={requisition.status !== "completed" || downloadingPdf}
                        onClick={async () => {
                            setDownloadingPdf(true);
                            setPdfError("");
                            try {
                                await requisitionApi.downloadPdf(requisition.id);
                            } catch (error) {
                                setPdfError(error instanceof Error ? error.message : "PDF trebovanja nije preuzet.");
                            } finally {
                                setDownloadingPdf(false);
                            }
                        }}
                        className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-yale-blue px-4 py-3 font-semibold text-yale-blue transition hover:bg-yale-blue/5 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:cursor-not-allowed disabled:opacity-45"
                    >
                        <Download className="h-4 w-4" />
                        {downloadingPdf ? "Preuzimanje..." : "Preuzmi PDF"}
                    </button>
                    {pdfError && <p role="alert" className="mt-2 text-sm text-red-700">{pdfError}</p>}
                    {user?.role === "BOOKKEEPER" && (
                        <button
                            type="button"
                            disabled={requisition.status !== "completed"}
                            onClick={() => {
                                setEmailRecipient("SALES_REP");
                                setEmailFeedback(null);
                                setEmailModalOpen(true);
                            }}
                            className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-yale-blue px-4 py-3 font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-45"
                        >
                            <Mail className="h-4 w-4" />
                            Pošalji e-mail
                        </button>
                    )}
                    {(requisition.note || requisition.statusNote) && (
                        <dl className="mt-4 space-y-3 rounded-2xl border border-dust-grey p-4">
                            {requisition.note && <div><dt className="text-xs font-bold uppercase text-graphite/45">Napomena trebovanja</dt><dd className="mt-1 whitespace-pre-wrap text-sm text-graphite">{requisition.note}</dd></div>}
                            {requisition.statusNote && <div><dt className="text-xs font-bold uppercase text-graphite/45">Napomena uz status</dt><dd className="mt-1 whitespace-pre-wrap text-sm text-graphite">{requisition.statusNote}</dd></div>}
                        </dl>
                    )}
                    <section className="mt-7">
                        <h3 className="flex items-center gap-2 text-lg font-bold text-yale-blue"><Package className="h-5 w-5" />Stavke</h3>
                        <div className="mt-3 overflow-hidden rounded-2xl border border-dust-grey">
                            <table className="w-full text-left">
                                <thead className="bg-yale-blue/[0.04] text-xs uppercase text-graphite/55"><tr><th className="px-4 py-3">Artikal</th><th className="px-4 py-3 text-right">Količina</th></tr></thead>
                                <tbody className="divide-y divide-dust-grey/75">
                                    {requisition.items.length === 0 && <tr><td colSpan={2} className="px-4 py-8 text-center text-sm text-graphite/55">Nema stavki.</td></tr>}
                                    {requisition.items.map((item) => <tr key={item.productCode}><td className="px-4 py-3"><span className="block font-semibold text-graphite">{item.productName}</span><span className="text-xs text-graphite/50">{item.productCode}</span></td><td className="px-4 py-3 text-right font-bold tabular-nums text-yale-blue">{item.quantity}</td></tr>)}
                                </tbody>
                            </table>
                        </div>
                    </section>
                </div>
                {requisition.actions.allowedStatusTransitions.length > 0 && (
                    <RequisitionStatusControl
                        key={`${requisition.id}-${requisition.status}`}
                        allowedTransitions={requisition.actions.allowedStatusTransitions}
                        onStatusChange={onStatusChange}
                    />
                )}
            </section>
            {emailModalOpen && (
                <div className="absolute inset-0 z-20 flex items-center justify-center bg-graphite/55 p-4">
                    <section
                        className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl sm:p-6"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="email-recipient-title"
                    >
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <h3 id="email-recipient-title" className="text-xl font-bold text-yale-blue">Pošalji trebovanje</h3>
                                <p className="mt-1 text-sm text-graphite/60">Izaberite primaoca PDF dokumenta.</p>
                            </div>
                            <button type="button" onClick={closeEmailModal} disabled={sendingEmail} aria-label="Zatvori" className="rounded-xl p-2 text-graphite hover:bg-dust-grey/35 disabled:opacity-50"><X className="h-5 w-5" /></button>
                        </div>

                        <div className="mt-5 space-y-3">
                            <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-dust-grey p-4 transition hover:border-stormy-teal">
                                <input
                                    type="radio"
                                    name="email-recipient"
                                    value="SALES_REP"
                                    checked={emailRecipient === "SALES_REP"}
                                    onChange={() => setEmailRecipient("SALES_REP")}
                                    className="mt-1"
                                />
                                <span>
                                    <span className="block font-semibold text-graphite">Komercijalista</span>
                                    <span className="mt-1 block text-sm text-graphite/65">{requisition.salesRep.email}</span>
                                </span>
                            </label>
                            <label className={`flex items-start gap-3 rounded-xl border border-dust-grey p-4 ${requisition.buyerEmail ? "cursor-pointer transition hover:border-stormy-teal" : "cursor-not-allowed bg-dust-grey/20 opacity-65"}`}>
                                <input
                                    type="radio"
                                    name="email-recipient"
                                    value="CUSTOMER"
                                    checked={emailRecipient === "CUSTOMER"}
                                    disabled={!requisition.buyerEmail}
                                    onChange={() => setEmailRecipient("CUSTOMER")}
                                    className="mt-1"
                                />
                                <span>
                                    <span className="block font-semibold text-graphite">Kupac</span>
                                    <span className="mt-1 block text-sm text-graphite/65">
                                        {requisition.buyerEmail ?? "Kupac nema e-mail adresu."}
                                    </span>
                                </span>
                            </label>
                        </div>

                        {emailFeedback && (
                            <p role="status" className={`mt-4 rounded-xl px-4 py-3 text-sm ${emailFeedback.type === "success" ? "bg-green-50 text-green-800" : "bg-red-50 text-red-700"}`}>
                                {emailFeedback.message}
                            </p>
                        )}

                        <div className="mt-6 flex justify-end gap-3">
                            <button type="button" onClick={closeEmailModal} disabled={sendingEmail} className="rounded-xl border border-dust-grey px-5 py-3 font-semibold text-graphite disabled:opacity-50">Otkaži</button>
                            <button
                                type="button"
                                onClick={sendEmail}
                                disabled={sendingEmail || emailFeedback !== null || (emailRecipient === "CUSTOMER" && !requisition.buyerEmail)}
                                className="rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white transition hover:bg-stormy-teal disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                {sendingEmail ? "Slanje..." : "Pošalji"}
                            </button>
                        </div>
                    </section>
                </div>
            )}
        </div>
    );
}
