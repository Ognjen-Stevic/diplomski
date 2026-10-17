import type { Customer, CustomerPayload } from "./types/customer.types";

export type CustomerFormValues = {
    objectName: string;
    companyName: string;
    pib: string;
    phone: string;
    email: string;
    address: string;
    googleMapsUrl: string;
    note: string;
};

export const CUSTOMER_FIELD_LIMITS = {
    objectName: 300,
    companyName: 300,
    pib: 9,
    phone: 25,
    email: 254,
    address: 300,
    note: 2000,
} as const;

export const EMPTY_CUSTOMER_FORM: CustomerFormValues = {
    objectName: "",
    companyName: "",
    pib: "",
    phone: "",
    email: "",
    address: "",
    googleMapsUrl: "",
    note: "",
};

export function customerToForm(customer: Customer): CustomerFormValues {
    return {
        objectName: customer.objectName ?? "",
        companyName: customer.companyName ?? "",
        pib: customer.pib ?? "",
        phone: customer.phone ?? "",
        email: customer.email ?? "",
        address: customer.address ?? "",
        googleMapsUrl: customer.googleMapsUrl ?? "",
        note: customer.note ?? "",
    };
}

export function validateCustomerForm(form: CustomerFormValues): string | null {
    const objectName = form.objectName.trim();
    const companyName = form.companyName.trim();
    const pib = form.pib.trim();
    const phone = form.phone.trim();
    const email = form.email.trim();
    const address = form.address.trim();

    if (!objectName) return "Naziv objekta je obavezan.";
    if (objectName.length > CUSTOMER_FIELD_LIMITS.objectName) {
        return `Naziv objekta može imati najviše ${CUSTOMER_FIELD_LIMITS.objectName} karaktera.`;
    }
    if (!companyName) return "Naziv kompanije je obavezan.";
    if (companyName.length > CUSTOMER_FIELD_LIMITS.companyName) {
        return `Naziv kompanije može imati najviše ${CUSTOMER_FIELD_LIMITS.companyName} karaktera.`;
    }
    if (!/^\d{9}$/.test(pib)) return "PIB mora sadržati tačno 9 cifara.";
    if (!address) return "Adresa je obavezna.";
    if (address.length > CUSTOMER_FIELD_LIMITS.address) {
        return `Adresa može imati najviše ${CUSTOMER_FIELD_LIMITS.address} karaktera.`;
    }

    if (email.length > CUSTOMER_FIELD_LIMITS.email) {
        return `E-mail adresa može imati najviše ${CUSTOMER_FIELD_LIMITS.email} karaktera.`;
    }
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        return "E-mail adresa nije ispravna.";
    }

    if (phone && !/^\+?[\d\s()/-]{6,25}$/.test(phone)) {
        return "Broj telefona nije ispravan.";
    }

    if (form.note.length > CUSTOMER_FIELD_LIMITS.note) {
        return `Napomena može imati najviše ${CUSTOMER_FIELD_LIMITS.note} karaktera.`;
    }

    if (form.googleMapsUrl.trim()) {
        try {
            const url = new URL(form.googleMapsUrl.trim());
            const hostname = url.hostname.toLowerCase();
            const isGoogleMapsHost =
                hostname === "google.com" ||
                hostname.endsWith(".google.com") ||
                hostname === "maps.app.goo.gl" ||
                hostname === "goo.gl";

            if (url.protocol !== "https:" || !isGoogleMapsHost) {
                return "Unesite ispravan Google Maps link.";
            }
        } catch {
            return "Unesite ispravan Google Maps link.";
        }
    }

    return null;
}

const optional = (value: string): string | null => value.trim() || null;

export function customerFormToPayload(form: CustomerFormValues): CustomerPayload {
    return {
        objectName: form.objectName.trim(),
        companyName: form.companyName.trim(),
        pib: form.pib.trim(),
        phone: optional(form.phone),
        email: optional(form.email),
        address: form.address.trim(),
        googleMapsUrl: optional(form.googleMapsUrl),
        note: optional(form.note),
    };
}
