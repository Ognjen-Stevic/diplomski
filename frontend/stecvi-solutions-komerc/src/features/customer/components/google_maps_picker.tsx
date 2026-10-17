import { LocateFixed, MapPin } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

const loadGoogleMaps = (apiKey: string) => {
    if (window.google?.maps) return Promise.resolve(window.google.maps);
    if (window.googleMapsLoader) return window.googleMapsLoader;

    window.googleMapsLoader = new Promise<GoogleMapsApi>((resolve, reject) => {
        const script = document.createElement("script");
        script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}`;
        script.async = true;
        script.onload = () => window.google?.maps ? resolve(window.google.maps) : reject(new Error("Google Maps nije dostupan."));
        script.onerror = () => reject(new Error("Google Maps nije moguće učitati."));
        document.head.appendChild(script);
    });

    return window.googleMapsLoader;
};

type Coordinates = { lat: number; lng: number };

const coordinatesFromUrl = (url: string): Coordinates | null => {
    const match = url.match(/[?&]q=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)/);
    if (!match) return null;

    return { lat: Number(match[1]), lng: Number(match[2]) };
};

type GoogleMapsApi = {
    Map: new (element: HTMLElement, options: Record<string, unknown>) => {
        addListener: (event: string, callback: (event: { latLng?: { lat: () => number; lng: () => number } }) => void) => void;
        panTo: (position: Coordinates) => void;
        setZoom: (zoom: number) => void;
    };
    Marker: new (options: Record<string, unknown>) => {
        addListener: (event: string, callback: () => void) => void;
        getPosition: () => { lat: () => number; lng: () => number } | null;
        setPosition: (position: Coordinates) => void;
    };
};

declare global {
    interface Window {
        google?: { maps: GoogleMapsApi };
        googleMapsLoader?: Promise<GoogleMapsApi>;
    }
}

export default function GoogleMapPicker({
    value = "",
    onChange,
}: {
    value?: string;
    onChange: (url: string) => void;
}) {
    const mapElement = useRef<HTMLDivElement>(null);
    const mapRef = useRef<InstanceType<GoogleMapsApi["Map"]> | null>(null);
    const markerRef = useRef<InstanceType<GoogleMapsApi["Marker"]> | null>(null);
    const mapsRef = useRef<GoogleMapsApi | null>(null);
    const onChangeRef = useRef(onChange);
    const initialCoordinates = coordinatesFromUrl(value);
    const initialCoordinatesRef = useRef(initialCoordinates);
    const [coordinates, setCoordinates] = useState<Coordinates | null>(
        initialCoordinates,
    );
    const [error, setError] = useState("");
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined;

    useEffect(() => {
        onChangeRef.current = onChange;
    }, [onChange]);

    const selectLocation = useCallback((position: Coordinates) => {
        markerRef.current?.setPosition(position);
        mapRef.current?.panTo(position);
        setCoordinates(position);
        onChangeRef.current(`https://www.google.com/maps?q=${position.lat.toFixed(6)},${position.lng.toFixed(6)}`);
    }, []);

    useEffect(() => {
        if (!apiKey || !mapElement.current) return;
        let active = true;

        loadGoogleMaps(apiKey)
            .then((maps) => {
                if (!active || !mapElement.current) return;
                mapsRef.current = maps;
                const initialPosition =
                    initialCoordinatesRef.current ?? { lat: 44.7866, lng: 20.4489 };
                const map = new maps.Map(mapElement.current, {
                    center: initialPosition,
                    zoom: 12,
                    mapTypeControl: false,
                    streetViewControl: false,
                    fullscreenControl: true,
                });
                const marker = new maps.Marker({ map, position: initialPosition, draggable: true });
                map.addListener("click", (event) => {
                    if (event.latLng) selectLocation({ lat: event.latLng.lat(), lng: event.latLng.lng() });
                });
                marker.addListener("dragend", () => {
                    const position = marker.getPosition();
                    if (position) selectLocation({ lat: position.lat(), lng: position.lng() });
                });
                mapRef.current = map;
                markerRef.current = marker;
            })
            .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Google Maps nije dostupan."));

        return () => { active = false; };
    }, [apiKey, selectLocation]);


    const useCurrentLocation = () => {
        if (!navigator.geolocation) {
            setError("Pregledač ne podržava određivanje lokacije.");
            return;
        }
        navigator.geolocation.getCurrentPosition(
            ({ coords }) => {
                selectLocation({ lat: coords.latitude, lng: coords.longitude });
                mapRef.current?.setZoom(17);
                setError("");
            },
            () => setError("Nije moguće pristupiti trenutnoj lokaciji."),
        );
    };

    if (!apiKey) {
        return (
            <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                Dodajte <code>VITE_GOOGLE_MAPS_API_KEY</code> u <code>.env</code> datoteku da biste prikazali mapu.
            </div>
        );
    }

    return (
        <div className="space-y-3">
            <div className="flex flex-col gap-2 sm:flex-row">
                <button type="button" onClick={useCurrentLocation} className="inline-flex items-center justify-center gap-2 rounded-xl border border-dust-grey px-4 py-2.5 font-semibold text-yale-blue transition hover:bg-dust-grey/30">
                    <LocateFixed className="h-4 w-4" /> Moja lokacija
                </button>
            </div>
            <div ref={mapElement} className="h-80 w-full overflow-hidden rounded-xl border border-dust-grey bg-dust-grey/20" aria-label="Mapa za izbor lokacije" />
            <p className="flex items-center gap-2 text-sm text-graphite/60">
                <MapPin className="h-4 w-4 text-stormy-teal" />
                {coordinates
                    ? `Izabrano: ${coordinates.lat.toFixed(6)}, ${coordinates.lng.toFixed(6)}`
                    : "Kliknite na mapu ili prevucite pin da izaberete lokaciju."}
            </p>
            {error && <p role="alert" className="text-sm font-semibold text-red-600">{error}</p>}
        </div>
    );
}
