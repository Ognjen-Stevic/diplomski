import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { requisitionApi } from "../requisition_api";
import type { RequisitionListItem } from "../types/requisition.types";

export function RequisitionEmailDialog({ requisition, onClose }: {
    requisition: RequisitionListItem; onClose: () => void;
}) {
    const [emailRecipient, setEmailRecipient] = useState<"SALES_REP" | "CUSTOMER">("SALES_REP");
    const [sendingEmail, setSendingEmail] = useState(false);
    const [emailFeedback, setEmailFeedback] = useState<{type: "success" | "error"; message: string} | null>(null);
    const inFlight = useRef(false);
    const dialog = useRef<HTMLElement>(null);
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const mounted = useRef(false);
    useEffect(() => {
        mounted.current = true;
        const previousFocus = document.activeElement as HTMLElement | null;
        dialog.current?.querySelector<HTMLElement>("button, input")?.focus();
        const handleKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.stopImmediatePropagation();
                if (!inFlight.current) onClose();
            }
            if (event.key === "Tab") {
                const controls = Array.from(dialog.current?.querySelectorAll<HTMLElement>("button:not(:disabled), input:not(:disabled)") ?? []);
                if (!controls.length) { event.preventDefault(); dialog.current?.focus(); return; }
                const first = controls[0]; const last = controls.at(-1);
                if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
                else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
            }
        };
        document.addEventListener("keydown", handleKey, true);
        return () => {
            mounted.current = false;
            if (timer.current) clearTimeout(timer.current);
            document.removeEventListener("keydown", handleKey, true);
            previousFocus?.focus();
        };
    }, [onClose]);
    const closeEmailModal = () => { if (!inFlight.current) onClose(); };
    const sendEmail = async () => {
        if (inFlight.current) return;
        inFlight.current = true;
        setSendingEmail(true); setEmailFeedback(null);
        try {
            await requisitionApi.sendPdfByEmail(requisition.id, emailRecipient);
            if (!mounted.current) return;
            setEmailFeedback({type: "success", message: "E-mail je uspešno poslat."});
            timer.current = setTimeout(onClose, 1400);
        } catch (reason) {
            if (mounted.current) setEmailFeedback({type: "error", message: reason instanceof Error ? reason.message : "E-mail nije poslat."});
        } finally {
            inFlight.current = false;
            if (mounted.current) setSendingEmail(false);
        }
    };
    return (

                <div className="absolute inset-0 z-20 flex items-center justify-center bg-graphite/55 p-4">
                    <section tabIndex={-1} ref={dialog}
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
                                    disabled={sendingEmail} onChange={() => { setEmailRecipient("SALES_REP"); setEmailFeedback(null); }}
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
                                    disabled={sendingEmail || !requisition.buyerEmail}
                                    onChange={() => { setEmailRecipient("CUSTOMER"); setEmailFeedback(null); }}
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
                                disabled={sendingEmail || emailFeedback?.type === "success" || (emailRecipient === "CUSTOMER" && !requisition.buyerEmail)}
                                className="rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white transition hover:bg-stormy-teal disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                {sendingEmail ? "Slanje..." : "Pošalji"}
                            </button>
                        </div>
                    </section>
                </div>

    );
}
