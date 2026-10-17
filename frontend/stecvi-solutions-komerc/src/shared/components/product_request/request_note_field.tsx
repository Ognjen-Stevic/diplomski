type RequestNoteFieldProps = {
    value: string;
    onChange: (value: string) => void;
};

export function RequestNoteField({ value, onChange }: RequestNoteFieldProps) {
    return (
        <section className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm sm:p-6">
            <label className="block text-sm font-bold text-graphite">
                Napomena <span className="font-normal text-graphite/50">(opciono)</span>
                <textarea
                    rows={3}
                    maxLength={1000}
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                    placeholder="Unesite dodatnu napomenu..."
                    className="mt-2 w-full resize-y rounded-xl border border-dust-grey bg-white px-4 py-3 font-normal text-graphite outline-none transition placeholder:text-graphite/40 focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
                />
            </label>
        </section>
    );
}
