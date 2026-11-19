import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import type { ReactNode } from "react";

import type { RequisitionListItem } from "../features/requisition/types/requisition.types";

const mocks = vi.hoisted(() => ({
    list: vi.fn(),
    customerList: vi.fn(),
    find: vi.fn(),
    update: vi.fn(),
    session: {},
    user: { id: 1, role: "BOOKKEEPER" },
}));

vi.mock("../features/auth/hooks/useAuth", () => ({
    useAuth: () => ({
        user: mocks.user,
        sessionId: mocks.session,
        hasPermission: () => true,
    }),
}));
vi.mock("../features/requisition/requisition_api", () => ({
    requisitionApi: {
        getMonthlyRequisitionPage: mocks.list,
        getMonthlyCustomerRequisitionPage: mocks.customerList,
        findById: mocks.find,
        updateStatus: mocks.update,
    },
}));
vi.mock("../app/layouts/app_layout", () => ({
    default: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock("../features/requisition/components/requisition_details_panel", () => ({
    RequisitionDetailsPanel: ({
        onStatusChange,
        onEdit,
    }: {
        onStatusChange: (status: string, note: string) => Promise<void>;
        onEdit: () => void;
    }) => <div>
        <button onClick={() => void onStatusChange("rejected_by_bookkeeper", "Reason").catch(() => undefined)}>
            Reject record
        </button>
        <button onClick={onEdit}>Edit record</button>
    </div>,
}));

import RequisitionHistoryPage from "../features/requisition/pages/requisition_history_page";
import RequisitionPage from "../features/requisition/pages/requisition_page";
import { useRequisitionReturnDestination } from "../features/requisition/hooks/use_requisition_route";
import { REQUISITION_STATUS_LABELS } from "../features/requisition/data/requisition.data";

const requisition: RequisitionListItem = {
    id: 1,
    revision: "r",
    salesRep: { id: 2, firstName: "A", lastName: "B", email: "a@b.test" },
    buyerId: 397,
    buyerName: "Unique Buyer",
    companyName: "Company",
    buyerEmail: null,
    type: "classic",
    status: "approved",
    createdAt: "2026-10-01",
    totalQuantity: 1,
    itemCount: 1,
    items: [],
    gratisReason: null,
    customReason: null,
    note: null,
    statusNote: null,
    actions: { canEdit: true, allowedStatusTransitions: ["rejected_by_bookkeeper"] },
};

const page = (requisitions: RequisitionListItem[]) => ({
    totalCount: requisitions.length,
    totalElements: requisitions.length,
    gratisCount: 0,
    normalCount: requisitions.length,
    totalPages: requisitions.length ? 1 : 0,
    pageNumber: 1,
    requisitions,
});

function LocationProbe() {
    const location = useLocation();
    return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function EditSuccessHarness() {
    const navigate = useNavigate();
    const returnDestination = useRequisitionReturnDestination();
    return <button onClick={() => navigate(returnDestination, { replace: true })}>Finish edit</button>;
}

beforeEach(() => {
    mocks.user.role = "BOOKKEEPER";
    mocks.list.mockReset();
    mocks.customerList.mockReset();
    mocks.find.mockReset();
    mocks.update.mockReset();
    mocks.find.mockResolvedValue(requisition);
});

describe("requisition mutation navigation", () => {
    it("refreshes customer history without losing its route or query parameters", async () => {
        mocks.customerList
            .mockResolvedValueOnce(page([requisition]))
            .mockResolvedValue(page([{ ...requisition, status: "rejected_by_bookkeeper" }]));
        mocks.update.mockResolvedValue({ ...requisition, status: "rejected_by_bookkeeper" });

        render(
            <MemoryRouter initialEntries={[
                "/trebovanja/kupac/397?year=2026&month=10&page=3&status=approved",
            ]}>
                <Routes>
                    <Route path="/trebovanja/kupac/:customerId" element={<>
                        <RequisitionHistoryPage />
                        <LocationProbe />
                    </>} />
                </Routes>
            </MemoryRouter>,
        );

        fireEvent.click(await screen.findByText("Unique Buyer"));
        fireEvent.click(await screen.findByText("Reject record"));

        await waitFor(() => expect(mocks.customerList).toHaveBeenCalledTimes(2));
        expect(screen.getByTestId("location")).toHaveTextContent(
            "/trebovanja/kupac/397?year=2026&month=10&page=3&status=approved",
        );
        expect(mocks.update).toHaveBeenCalledWith(1, "rejected_by_bookkeeper", "Reason", "r");
    });

    it("refreshes the main requisition list without navigating away", async () => {
        mocks.list
            .mockResolvedValueOnce(page([requisition]))
            .mockResolvedValue(page([]));
        mocks.update.mockResolvedValue({ ...requisition, status: "rejected_by_bookkeeper" });

        render(
            <MemoryRouter initialEntries={["/trebovanja?year=2026&month=10&status=approved"]}>
                <Routes>
                    <Route path="/trebovanja" element={<>
                        <RequisitionPage />
                        <LocationProbe />
                    </>} />
                </Routes>
            </MemoryRouter>,
        );

        fireEvent.click(await screen.findByText("Unique Buyer"));
        fireEvent.click(await screen.findByText("Reject record"));

        await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(screen.queryByText("Unique Buyer")).not.toBeInTheDocument());
        expect(screen.getByTestId("location")).toHaveTextContent(
            "/trebovanja?year=2026&month=10&status=approved",
        );
    });

    it("does not navigate or refresh when a status update fails", async () => {
        mocks.customerList.mockResolvedValue(page([requisition]));
        mocks.update.mockRejectedValue(new Error("Update failed"));

        render(
            <MemoryRouter initialEntries={["/trebovanja/kupac/397?year=2026&month=10"]}>
                <Routes>
                    <Route path="/trebovanja/kupac/:customerId" element={<>
                        <RequisitionHistoryPage />
                        <LocationProbe />
                    </>} />
                </Routes>
            </MemoryRouter>,
        );

        fireEvent.click(await screen.findByText("Unique Buyer"));
        fireEvent.click(await screen.findByText("Reject record"));

        await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
        expect(mocks.customerList).toHaveBeenCalledTimes(1);
        expect(screen.getByTestId("location")).toHaveTextContent(
            "/trebovanja/kupac/397?year=2026&month=10",
        );
    });

    it("preserves the origin when opening the shared edit workflow", async () => {
        mocks.customerList.mockResolvedValue(page([requisition]));

        render(
            <MemoryRouter initialEntries={[
                "/trebovanja/kupac/397?year=2026&month=10&page=3#details",
            ]}>
                <Routes>
                    <Route path="/trebovanja/kupac/:customerId" element={<>
                        <RequisitionHistoryPage />
                        <LocationProbe />
                    </>} />
                    <Route path="/trebovanja/:requisitionId/izmena" element={<LocationProbe />} />
                </Routes>
            </MemoryRouter>,
        );

        fireEvent.click(await screen.findByText("Unique Buyer"));
        fireEvent.click(await screen.findByText("Edit record"));

        expect(screen.getByTestId("location")).toHaveTextContent("/trebovanja/1/izmena");
    });

    it("returns a successful edit to its exact requisition origin", () => {
        render(
            <MemoryRouter initialEntries={[{
                pathname: "/trebovanja/1/izmena",
                state: {
                    returnTo: "/trebovanja/kupac/397?year=2026&month=10&page=3#details",
                },
            }]}>
                <Routes>
                    <Route path="/trebovanja/:requisitionId/izmena" element={<EditSuccessHarness />} />
                    <Route path="/trebovanja/kupac/:customerId" element={<LocationProbe />} />
                </Routes>
            </MemoryRouter>,
        );

        fireEvent.click(screen.getByText("Finish edit"));

        expect(screen.getByTestId("location")).toHaveTextContent(
            "/trebovanja/kupac/397?year=2026&month=10&page=3",
        );
    });
});

describe("requisition creation visibility", () => {
    it("does not show the creation button to a CEO even with create permission", async () => {
        mocks.user.role = "CEO";
        mocks.list.mockResolvedValue(page([]));

        render(
            <MemoryRouter initialEntries={["/trebovanja"]}>
                <Routes>
                    <Route path="/trebovanja" element={<RequisitionPage />} />
                </Routes>
            </MemoryRouter>,
        );

        await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(1));
        expect(screen.queryByRole("button", { name: "Novo trebovanje" })).not.toBeInTheDocument();
    });

    it("keeps the creation button for another role with create permission", async () => {
        mocks.user.role = "SALES_REP";
        mocks.list.mockResolvedValue(page([]));

        render(
            <MemoryRouter initialEntries={["/trebovanja"]}>
                <Routes>
                    <Route path="/trebovanja" element={<RequisitionPage />} />
                </Routes>
            </MemoryRouter>,
        );

        expect(await screen.findByRole("button", { name: "Novo trebovanje" })).toBeInTheDocument();
    });
});

describe("bookkeeper requisition visibility", () => {
    it("offers and applies the rejected-by-bookkeeper status filter", async () => {
        mocks.user.role = "BOOKKEEPER";
        mocks.list.mockResolvedValue(page([]));

        render(
            <MemoryRouter initialEntries={["/trebovanja"]}>
                <Routes>
                    <Route path="/trebovanja" element={<RequisitionPage />} />
                </Routes>
            </MemoryRouter>,
        );

        const filter = await screen.findByRole("combobox", { name: "Status trebovanja" });
        const option = screen.getByRole("option", {
            name: REQUISITION_STATUS_LABELS.rejected_by_bookkeeper,
        }) as HTMLOptionElement;
        expect(option.value).toBe("rejected_by_bookkeeper");

        fireEvent.change(filter, { target: { value: "rejected_by_bookkeeper" } });

        await waitFor(() => expect(mocks.list).toHaveBeenLastCalledWith(
            expect.any(Number),
            expect.any(Number),
            1,
            "",
            "rejected_by_bookkeeper",
            expect.any(AbortSignal),
        ));
    });
});
