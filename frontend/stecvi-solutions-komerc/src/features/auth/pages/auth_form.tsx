import { useState, type ChangeEvent, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { useAuth } from "../hooks/useAuth";
import { AuthInput } from "../components/auth_input";

export default function AuthForm() {
    const [formData, setFormData] = useState({ email: "", password: "" });
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = event.target;
        setFormData((previous) => ({ ...previous, [name]: value }));
        if (error) setError("");
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!formData.email.trim() || !formData.password) {
            setError("Unesite e-mail adresu i lozinku.");
            return;
        }

        setIsSubmitting(true);
        try {
            await login({ email: formData.email.trim(), password: formData.password });
            const from = (location.state as { from?: string } | null)?.from ?? "/";
            navigate(from, { replace: true });
        } catch (caughtError) {
            setError(caughtError instanceof Error ? caughtError.message : "Prijava nije uspela.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <main className="flex min-h-screen items-center justify-center bg-dust-grey/30 px-4 py-10">
            <section className="w-full max-w-md rounded-2xl border border-dust-grey bg-white p-8 shadow-lg">
                <div className="mb-8 text-center">
                    <h1 className="text-3xl font-bold text-yale-blue">Prijava</h1>
                    <p className="mt-2 text-sm text-graphite">Unesite podatke za pristup nalogu.</p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-5">
                    <AuthInput label="E-mail adresa" name="email" type="email" value={formData.email}
                        onChange={handleChange} autoComplete="email" placeholder="ime@firma.rs" />
                    <AuthInput label="Lozinka" name="password" type="password" value={formData.password}
                        onChange={handleChange} autoComplete="current-password" placeholder="Unesite lozinku" />

                    {error && <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</p>}

                    <button type="submit" disabled={isSubmitting}
                        className="w-full rounded-lg bg-yale-blue px-4 py-3 font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60">
                        {isSubmitting ? "Prijavljivanje..." : "Prijavi se"}
                    </button>
                </form>

            </section>
        </main>
    );
}

