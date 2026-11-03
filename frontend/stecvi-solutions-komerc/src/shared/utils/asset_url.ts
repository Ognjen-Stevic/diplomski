const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, "") ?? "";
export function resolveProductPictureUrl(pictureUrl: string) {
    if (/^https?:\/\//i.test(pictureUrl)) return pictureUrl;
    return `${API_URL}/${pictureUrl.replace(/^\//, "")}`;
}
