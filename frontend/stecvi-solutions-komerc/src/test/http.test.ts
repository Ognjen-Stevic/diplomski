import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest } from "../app/api/http_client";
import { authApi } from "../features/auth/auth_api";
beforeEach(() => { vi.restoreAllMocks();localStorage.clear(); });
it("multipart bodies retain browser-generated content types and no-content succeeds", async () => {
 const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue(new Response(null,{status:204}));const form=new FormData();form.append("image",new Blob(["image"]));await expect(apiRequest("/api/products",{method:"POST",body:form})).resolves.toBeUndefined();expect(fetch.mock.calls[0][1]?.headers).not.toHaveProperty("Content-Type");expect(fetch.mock.calls[0][1]?.body).toBe(form);
});
it("a late 401 cannot invalidate a newer login", async () => {
 localStorage.setItem("stecvi_access_token","old");vi.spyOn(globalThis,"fetch").mockImplementation(async () => {localStorage.setItem("stecvi_access_token","new");return new Response(null,{status:401})});await expect(apiRequest("/api/test")).rejects.toMatchObject({status:401});expect(authApi.getToken()).toBe("new");
});
