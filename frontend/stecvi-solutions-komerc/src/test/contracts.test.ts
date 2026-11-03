import { beforeEach, expect, it, vi } from "vitest";
const calls=vi.hoisted(() => ({json:vi.fn(),binary:vi.fn()}));
vi.mock("../app/api/http_client", () => ({apiRequest:calls.json,authenticatedFetch:calls.binary}));
import { reportApi } from "../features/report/report_api";
import { customerApi } from "../features/customer/customer_api";
import { requisitionApi } from "../features/requisition/requisition_api";
import { DASHBOARD_ALERTS_REFRESH_EVENT } from "../features/dashboard/alert_api";
import { resolveProductPictureUrl } from "../shared/utils/asset_url";
beforeEach(() => { vi.clearAllMocks(); });
it("report transports typed numeric metrics independently from presentation summaries", async () => {
 calls.json.mockResolvedValue({year:2026,month:10,salesRep:null,summary:{totalBottles:"1",totalJugs:"2",totalLiters:"3",salesRepIncentive:"240",managerIncentive:"60",totalIncentive:"300"},products:[]});
 const report=await reportApi.getMonthlyReport(2026,10);expect(report.metrics).toMatchObject({salesRepIncentive:240,managerIncentive:60,totalIncentive:300});expect(report.summary.find(x => x.id === "company-incentive")?.value).toBe(report.metrics.totalIncentive);
});
it("malformed customer statuses fail before exposing business actions", async () => {
 calls.json.mockResolvedValue({status:"UNKNOWN"});await expect(customerApi.findById(1)).rejects.toThrow("status");
});
it("successful requisition writes notify dashboard alerts", async () => {
 const listener=vi.fn();window.addEventListener(DASHBOARD_ALERTS_REFRESH_EVENT,listener);calls.json.mockResolvedValue({id:1});await requisitionApi.create({customerId:1,type:"NORMAL",gratisReason:null,customReason:null,note:null,items:[{productCode:"P",quantity:1}]});expect(listener).toHaveBeenCalledTimes(1);window.removeEventListener(DASHBOARD_ALERTS_REFRESH_EVENT,listener);
});
it("binary Excel filename and object URL cleanup remain intact", async () => {
 calls.binary.mockResolvedValue(new Response(new Blob(["excel"]),{headers:{"Content-Disposition":"attachment; filename*=UTF-8''report%20test.xlsx"}}));
 Object.defineProperty(URL,"createObjectURL",{configurable:true,value:vi.fn(() => "blob:test")});Object.defineProperty(URL,"revokeObjectURL",{configurable:true,value:vi.fn()});const click=vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(() => {});
 await reportApi.downloadMonthlyReport(2026,10);expect(click).toHaveBeenCalledTimes(1);expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:test");expect(document.querySelector('a[download]')).toBeNull();expect(calls.binary.mock.calls[0][0]).toContain("monthly/export?year=2026&month=10");
});
it("moving asset resolution preserves absolute and server-relative product images", () => {expect(resolveProductPictureUrl("https://example.test/p.png")).toBe("https://example.test/p.png");expect(resolveProductPictureUrl("products/p.png")).toContain("/products/p.png");});
