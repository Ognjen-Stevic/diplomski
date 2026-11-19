import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest, downloadAuthenticatedFile } from "../app/api/http_client";
import { authApi } from "../features/auth/auth_api";
beforeEach(() => { vi.restoreAllMocks();localStorage.clear(); });
it("multipart bodies retain browser-generated content types and no-content succeeds", async () => {
 const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue(new Response(null,{status:204}));const form=new FormData();form.append("image",new Blob(["image"]));await expect(apiRequest("/api/products",{method:"POST",body:form})).resolves.toBeUndefined();expect(fetch.mock.calls[0][1]?.headers).not.toHaveProperty("Content-Type");expect(fetch.mock.calls[0][1]?.body).toBe(form);
});
it("a late 401 cannot invalidate a newer login", async () => {
 localStorage.setItem("stecvi_access_token","old");vi.spyOn(globalThis,"fetch").mockImplementation(async () => {localStorage.setItem("stecvi_access_token","new");return new Response(null,{status:401})});await expect(apiRequest("/api/test")).rejects.toMatchObject({status:401});expect(authApi.getToken()).toBe("new");
});
it("authenticated downloads use the server filename and always clean up the Blob URL", async () => {
 localStorage.setItem("stecvi_access_token","token");
 vi.spyOn(globalThis,"fetch").mockResolvedValue(new Response(new TextEncoder().encode("file"),{headers:{"Content-Disposition":"attachment; filename*=UTF-8''report%20test.xlsx"}}));
 Object.defineProperty(URL,"createObjectURL",{configurable:true,value:vi.fn(() => "blob:test")});Object.defineProperty(URL,"revokeObjectURL",{configurable:true,value:vi.fn()});const create=vi.mocked(URL.createObjectURL);const revoke=vi.mocked(URL.revokeObjectURL);const click=vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(() => {});
 await downloadAuthenticatedFile("/api/report","fallback.xlsx","Download failed.");
 expect(create).toHaveBeenCalledTimes(1);expect(click).toHaveBeenCalledTimes(1);expect(revoke).toHaveBeenCalledWith("blob:test");expect(document.querySelector('a[download]')).toBeNull();
});
it("authenticated downloads preserve API errors and do not allocate a Blob URL", async () => {
 localStorage.setItem("stecvi_access_token","token");
 vi.spyOn(globalThis,"fetch").mockResolvedValue(new Response(JSON.stringify({message:"Not available"}),{status:409,headers:{"Content-Type":"application/json"}}));
 Object.defineProperty(URL,"createObjectURL",{configurable:true,value:vi.fn()});const create=vi.mocked(URL.createObjectURL);
 await expect(downloadAuthenticatedFile("/api/report","fallback.xlsx","Download failed.")).rejects.toMatchObject({message:"Not available",status:409});expect(create).not.toHaveBeenCalled();
});
