import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect,it,vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
const mocks=vi.hoisted(() => ({list:vi.fn(),update:vi.fn(),session:{},user:{id:1,role:"BOOKKEEPER"}}));
vi.mock("../features/auth/hooks/useAuth",() => ({useAuth:()=>({user:mocks.user,sessionId:mocks.session,hasPermission:()=>true})}));
vi.mock("../features/requisition/requisition_api",()=>({requisitionApi:{getMonthlyRequisition:mocks.list,updateStatus:mocks.update}}));
vi.mock("../app/layouts/app_layout",()=>({default:({children}:{children:ReactNode})=><>{children}</>}));
vi.mock("../features/requisition/components/requisition_details_panel",()=>({RequisitionDetailsPanel:({onStatusChange}:{onStatusChange:(status:string,note:string)=>Promise<void>})=><button onClick={()=>void onStatusChange("rejected_by_bookkeeper","Reason")}>Reject record</button>}));
import RequisitionPage from "../features/requisition/pages/requisition_page";
it("bookkeeper rejection reloads authoritative counts and removes the record and details",async()=>{
 mocks.list.mockResolvedValueOnce({totalCount:1,gratisCount:0,requisitions:[{id:1,revision:"r",buyerName:"Unique Buyer",companyName:"Company",type:"classic",status:"approved",createdAt:"2026-10-01",totalQuantity:1,itemCount:1,items:[],actions:{canEdit:false,allowedStatusTransitions:[]}}]}).mockResolvedValue({totalCount:0,gratisCount:0,requisitions:[]});mocks.update.mockResolvedValue({});
 render(<MemoryRouter><RequisitionPage/></MemoryRouter>);fireEvent.click(await screen.findByText("Unique Buyer"));fireEvent.click(screen.getByText("Reject record"));await waitFor(()=>expect(mocks.list).toHaveBeenCalledTimes(2));await waitFor(()=>expect(screen.queryByText("Unique Buyer")).not.toBeInTheDocument());expect(screen.queryByText("Reject record")).not.toBeInTheDocument();expect(mocks.update).toHaveBeenCalledWith(1,"rejected_by_bookkeeper","Reason","r");
});
