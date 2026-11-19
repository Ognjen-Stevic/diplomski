import { beforeEach, expect, it, vi } from "vitest";
const calls=vi.hoisted(() => ({json:vi.fn(),download:vi.fn()}));
vi.mock("../app/api/http_client", () => ({apiRequest:calls.json,downloadAuthenticatedFile:calls.download}));
import { reportApi } from "../features/report/report_api";
import { customerApi } from "../features/customer/customer_api";
import { requisitionApi } from "../features/requisition/requisition_api";
import { DASHBOARD_ALERTS_REFRESH_EVENT } from "../features/dashboard/alert_api";
import { resolveProductPictureUrl } from "../shared/utils/asset_url";
import { riskApi } from "../features/ai/apis/risk_api";
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
 calls.download.mockResolvedValue(undefined);
 await reportApi.downloadMonthlyReport(2026,10);expect(calls.download.mock.calls[0][0]).toContain("monthly/export?year=2026&month=10");expect(calls.download.mock.calls[0][1]).toBe("monthly-report-2026-10.xlsx");
});
it("moving asset resolution preserves absolute and server-relative product images", () => {expect(resolveProductPictureUrl("https://example.test/p.png")).toBe("https://example.test/p.png");expect(resolveProductPictureUrl("products/p.png")).toContain("/products/p.png");});
it("AI risk pagination always requests twenty customers", async () => {
 calls.json.mockResolvedValue({content:[],total:0,page:2,size:20});
 await riskApi.list("risk=HIGH&page=2", new AbortController().signal);
 const parameters = new URLSearchParams(String(calls.json.mock.calls[0][0]).split("?")[1]);
 expect(parameters.get("risk")).toBe("HIGH");expect(parameters.get("page")).toBe("2");expect(parameters.get("size")).toBe("20");
});
it("customer pagination defaults to twenty-four customers", async () => {
 calls.json.mockResolvedValue({content:[],totalElements:0,totalPages:0,page:0,size:24});
 await customerApi.findAll();
 const parameters = new URLSearchParams(String(calls.json.mock.calls[0][0]).split("?")[1]);
 expect(parameters.get("page")).toBe("0");expect(parameters.get("size")).toBe("24");
});
it("requisition pagination sends filters before server-side pagination", async () => {
 calls.json.mockResolvedValue({totalCount:0,totalElements:0,gratisCount:0,normalCount:0,totalPages:0,pageNumber:1,content:[]});
 await requisitionApi.getMonthlyRequisitionPage(2026,11,2,"Market","completed");
 const parameters = new URLSearchParams(String(calls.json.mock.calls[0][0]).split("?")[1]);
 expect(parameters.get("page")).toBe("1");expect(parameters.get("search")).toBe("Market");expect(parameters.get("status")).toBe("COMPLETED");
});
it("dashboard statistics use the lightweight aggregate endpoint", async () => {
 calls.json.mockResolvedValue({completedCount:3,completedGratisCount:1,cancelledCount:2});
 await requisitionApi.getDashboardMonthlyStatistics(2026,11);
 expect(calls.json.mock.calls[0][0]).toBe("/api/requisitions/dashboard-statistics?year=2026&month=11");
});
it("customer requisition history uses the lightweight paged endpoint", async () => {
 calls.json.mockResolvedValue({totalElements:0,totalPages:0,pageNumber:0,content:[]});
 await requisitionApi.getMonthlyCustomerRequisitionPage(7,2026,11,1);
 expect(calls.json.mock.calls[0][0]).toBe("/api/requisitions/customer/7/page?year=2026&month=11&page=0");
});
