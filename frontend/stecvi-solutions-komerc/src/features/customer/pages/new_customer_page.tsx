import { SessionScope } from "../../../shared/components/session_scope";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import { CustomerForm } from "../components/customer_form";
import { customerApi } from "../customer_api";
import {
    customerFormToPayload,
    customerToForm,
    EMPTY_CUSTOMER_FORM,
    validateCustomerForm,
    type CustomerFormValues,
} from "../customer_form";

export default function NewCustomerPage() {
    const { customerId } = useParams();
    return <SessionScope><CustomerPageIdentity key={customerId ?? "new"} /></SessionScope>;
}
function CustomerPageIdentity() {
    const navigate = useNavigate();
    const { customerId } = useParams();
    const { user, hasPermission } = useAuth();
    const isEditMode = customerId !== undefined;
    const parsedId = isEditMode ? Number(customerId) : null;
    const isValidId = !isEditMode || (
        parsedId !== null && Number.isSafeInteger(parsedId) && parsedId > 0
    );
    const hasAccess = hasPermission(
        isEditMode ? PERMISSIONS.CUSTOMERS_UPDATE : PERMISSIONS.CUSTOMERS_CREATE,
    );

    const [form, setForm] = useState<CustomerFormValues>(EMPTY_CUSTOMER_FORM);
    const [loading, setLoading] = useState(isEditMode);
    const [loadError, setLoadError] = useState("");
    const [error, setError] = useState("");
    const guard = useRef(false);
    const mounted = useRef(false);
    useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!user || !hasAccess || !isEditMode || !isValidId || parsedId === null) return;

        let active = true;
        const controller = new AbortController();

        customerApi.findById(parsedId, controller.signal)
            .then((customer) => {
                if (active) setForm(customerToForm(customer));
            })
            .catch((requestError: unknown) => {
                if (!active) return;
                setLoadError(
                    requestError instanceof Error ? requestError.message : "Kupac nije učitan.",
                );
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
            controller.abort();
        };
    }, [user, hasAccess, isEditMode, isValidId, parsedId]);

    const updateField = (field: keyof CustomerFormValues, value: string) => {
        setForm((current) => ({ ...current, [field]: value }));
        setError("");
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (guard.current || loading || loadError || !isValidId || !hasAccess) return;

        const validationError = validateCustomerForm(form);
        if (validationError) {
            setError(validationError);
            return;
        }

        guard.current = true;
        setSubmitting(true);
        setError("");
        try {
            const payload = customerFormToPayload(form);
            if (isEditMode && parsedId !== null) {
                await customerApi.update(parsedId, payload);
            } else {
                await customerApi.create(payload);
            }
            if (mounted.current) navigate("/kupci", { replace: true });
        } catch (requestError: unknown) {
            setError(
                requestError instanceof Error
                    ? requestError.message
                    : "Došlo je do greške prilikom čuvanja kupca.",
            );
        } finally {
            guard.current = false;
            if (mounted.current) setSubmitting(false);
        }
    };

    if (!user) return <Navigate to="/login" replace />;
    if (!hasAccess || !isValidId) return <Navigate to="/kupci" replace />;

    return (
        <AppLayout>
            <main className="min-h-full px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-8 lg:pb-12">
                <div className="mx-auto w-full max-w-4xl">
                    <button
                        type="button"
                        onClick={() => navigate("/kupci")}
                        className="inline-flex items-center gap-2 font-semibold text-yale-blue"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Nazad na kupce
                    </button>

                    <div className="mt-6">
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">
                            Evidencija kupaca
                        </p>
                        <h1 className="mt-1 text-3xl font-bold text-yale-blue">
                            {isEditMode ? "Izmena kupca" : "Novi kupac"}
                        </h1>
                        <p className="mt-2 text-graphite/60">
                            Polja označena zvezdicom su obavezna.
                        </p>
                    </div>

                    {loading ? (
                        <p className="mt-8" role="status">Učitavanje kupca...</p>
                    ) : loadError ? (
                        <LoadError message={loadError} onBack={() => navigate("/kupci")} />
                    ) : (
                        <CustomerForm
                            values={form}
                            error={error}
                            isEditMode={isEditMode}
                            submitting={submitting}
                            onChange={updateField}
                            onCancel={() => navigate("/kupci")}
                            onSubmit={handleSubmit}
                        />
                    )}
                </div>
            </main>
        </AppLayout>
    );
}

type LoadErrorProps = {
    message: string;
    onBack: () => void;
};

function LoadError({ message, onBack }: LoadErrorProps) {
    return (
        <div role="alert" className="mt-7 rounded-2xl border border-red-200 bg-red-50 p-5">
            <p className="font-semibold text-red-700">Kupac nije mogao biti učitan.</p>
            <p className="mt-2 text-red-700">{message}</p>
            <button
                type="button"
                onClick={onBack}
                className="mt-4 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white"
            >
                Nazad na kupce
            </button>
        </div>
    );
}
