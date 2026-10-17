import type { ChangeEvent } from "react";

type AuthInputProps = {
    label: string;
    name: string;
    type: string;
    value: string;
    placeholder: string;
    autoComplete: string;
    onChange: (event: ChangeEvent<HTMLInputElement>) => void;
};

export function AuthInput({ label, name, ...props }: AuthInputProps) {
    return (
        <div>
            <label htmlFor={name} className="mb-2 block text-sm font-semibold text-graphite">{label}</label>
            <input id={name} name={name} {...props}
                className="w-full rounded-lg border border-dust-grey bg-white px-4 py-3 text-graphite outline-none transition placeholder:text-graphite/50 focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20" />
        </div>
    );
}