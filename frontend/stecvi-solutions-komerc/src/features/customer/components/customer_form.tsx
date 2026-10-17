import { Save } from "lucide-react";
import type { FormEvent } from "react";

import { CUSTOMER_FIELD_LIMITS, type CustomerFormValues } from "../customer_form";
import GoogleMapPicker from "./google_maps_picker";

const inputClassName =
    "mt-2 w-full rounded-xl border border-dust-grey bg-white px-4 py-3 text-graphite outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20";

type CustomerFormProps = {
    values: CustomerFormValues;
    error: string;
    isEditMode: boolean;
    submitting: boolean;
    onChange: (field: keyof CustomerFormValues, value: string) => void;
    onCancel: () => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CustomerForm({
    values,
    error,
    isEditMode,
    submitting,
    onChange,
    onCancel,
    onSubmit,
}: CustomerFormProps) {
    return (
        <form
            onSubmit={onSubmit}
            className="mt-7 rounded-2xl border border-dust-grey bg-white p-5 shadow-sm sm:p-7"
        >
            <div className="grid gap-6 sm:grid-cols-2">
                <Field label="Naziv objekta *" required maxLength={CUSTOMER_FIELD_LIMITS.objectName} value={values.objectName} onChange={(value) => onChange("objectName", value)} />
                <Field label="Naziv kompanije *" required maxLength={CUSTOMER_FIELD_LIMITS.companyName} value={values.companyName} onChange={(value) => onChange("companyName", value)} />
                <Field label="PIB *" required maxLength={CUSTOMER_FIELD_LIMITS.pib} inputMode="numeric" pattern="[0-9]{9}" value={values.pib} onChange={(value) => onChange("pib", value)} />
                <Field label="Telefon" type="tel" maxLength={CUSTOMER_FIELD_LIMITS.phone} value={values.phone} onChange={(value) => onChange("phone", value)} />
                <Field label="E-mail kupca" type="email" maxLength={CUSTOMER_FIELD_LIMITS.email} value={values.email} onChange={(value) => onChange("email", value)} />

                <div className="sm:col-span-2">
                    <Field label="Adresa *" required maxLength={CUSTOMER_FIELD_LIMITS.address} value={values.address} onChange={(value) => onChange("address", value)} />
                </div>

                <fieldset className="sm:col-span-2">
                    <legend className="mb-2 font-semibold text-graphite">Lokacija na Google mapi</legend>
                    <GoogleMapPicker value={values.googleMapsUrl} onChange={(value) => onChange("googleMapsUrl", value)} />
                </fieldset>

                <label className="font-semibold text-graphite sm:col-span-2">
                    Napomena
                    <textarea
                        rows={5}
                        maxLength={CUSTOMER_FIELD_LIMITS.note}
                        value={values.note}
                        onChange={(event) => onChange("note", event.target.value)}
                        className={`${inputClassName} resize-y`}
                    />
                </label>
            </div>

            {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}</p>}

            <div className="mt-7 flex flex-wrap justify-end gap-3">
                <button type="button" disabled={submitting} onClick={onCancel} className="rounded-xl border border-dust-grey px-5 py-3 font-semibold disabled:opacity-60">
                    Otkaži
                </button>
                <button disabled={submitting} type="submit" className="inline-flex items-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60">
                    <Save className="h-5 w-5" />
                    {submitting ? "Čuvanje..." : isEditMode ? "Sačuvaj izmene" : "Sačuvaj kupca"}
                </button>
            </div>
        </form>
    );
}

type FieldProps = {
    label: string;
    value: string;
    onChange: (value: string) => void;
    required?: boolean;
    type?: string;
    maxLength?: number;
    inputMode?: "numeric" | "text" | "tel" | "email";
    pattern?: string;
};

function Field({ label, value, onChange, required = false, type = "text", maxLength = 300, inputMode, pattern }: FieldProps) {
    return (
        <label className="font-semibold text-graphite">
            {label}
            <input
                type={type}
                required={required}
                maxLength={maxLength}
                inputMode={inputMode}
                pattern={pattern}
                value={value}
                onChange={(event) => onChange(event.target.value)}
                className={inputClassName}
            />
        </label>
    );
}
