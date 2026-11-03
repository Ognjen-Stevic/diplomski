import type { FormEvent } from "react";
import { Search } from "lucide-react";

type CustomerSearchProps = {
    value: string;
    onChange: (value: string) => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CustomerSearch({ value, onChange, onSubmit }: CustomerSearchProps) {
    return (
        <form
            onSubmit={onSubmit}
            className="relative"
        >
            <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-graphite/45" />

            <input
                type="search"
                value={value}
                onChange={(event) =>
                    onChange(event.target.value)
                }
                placeholder="Pretraži kupca po nazivu ili gradu"
                aria-label="Pretraga kupaca"
                className="w-full rounded-2xl border border-dust-grey bg-white py-4 pl-12 pr-28 shadow-sm outline-none transition placeholder:text-graphite/40 focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
            />

            <button
                type="submit"
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-xl bg-yale-blue px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
            >
                Pretraži
            </button>
        </form>
    );
}