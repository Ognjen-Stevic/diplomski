import { UserRound } from "lucide-react";

export default function NoUserFound() {
    return (
        <div className="rounded-2xl border border-dashed border-dust-grey bg-white px-6 py-14 text-center">
            <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-dust-grey/35 text-yale-blue">
                <UserRound className="h-7 w-7" />
            </span>
            <h2 className="mt-5 text-lg font-bold text-yale-blue">
                Nema pronađenih kupaca
            </h2>
            <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-graphite/60">
                Promenite kriterijum pretrage ili unesite novog kupca ako
                objekat još nije evidentiran.
            </p>
        </div>
    )
}
