import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
import type { ReactNode } from "react";
const mocks = vi.hoisted(() => ({
  auth: {
    user: { id: 1, role: "CEO", name: "CEO" },
    sessionId: {},
    hasPermission: () => true,
  },
  findById: vi.fn(),
  findAll: vi.fn(),
  update: vi.fn(),
  employees: vi.fn(),
  managers: vi.fn(),
  create: vi.fn(),
  report: vi.fn(),
  monthly: vi.fn(),
}));
vi.mock("../features/auth/hooks/useAuth", () => ({
  useAuth: () => mocks.auth,
}));
vi.mock("../features/customer/customer_api", () => ({
  customerApi: {
    findById: mocks.findById,
    findAll: mocks.findAll,
    update: mocks.update,
  },
}));
vi.mock("../features/employees/employees_api", () => ({
  employeesApi: {
    findAll: mocks.employees,
    findActiveManagers: mocks.managers,
    create: mocks.create,
  },
}));
vi.mock("../features/report/report_api", () => ({
  reportApi: { getMonthlyReport: mocks.report },
}));
vi.mock("../features/requisition/requisition_api", () => ({
  requisitionApi: { getDashboardMonthlyStatistics: mocks.monthly },
}));
vi.mock("../app/layouts/app_layout", () => ({
  default: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock("../features/customer/components/google_maps_picker", () => ({
  default: () => null,
}));
vi.mock("../features/employees/components/employee_form", () => ({
  EmployeeForm: ({ onCreate }: { onCreate: (value: object) => void }) => (
    <button onClick={() => onCreate({ firstName: "New" })}>
      Confirm employee
    </button>
  ),
}));
import { useCustomerSelector } from "../features/customer/use_customer_selector";
import NewCustomerPage from "../features/customer/pages/new_customer_page";
import EmployeesPage from "../features/employees/pages/employees_page";
import { useMonthlyDashboardStats } from "../features/dashboard/hooks/use_monthly_dashboard_stats";
import { useSessionRequest } from "../shared/hooks/use_session_request";
import { useDialog } from "../shared/hooks/use_dialog";
import { RequisitionStatusControl } from "../features/requisition/components/requisition_status_control";
import { readReportNavigation } from "../features/report/utils/report_navigation";
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
};
const customer = (id: number) => ({
  id,
  objectName: `Customer ${id}`,
  companyName: "Company",
  pib: "123456789",
  phone: "",
  email: "",
  address: "Address",
  googleMapsUrl: "",
  note: "",
  status: "approved",
});
beforeEach(() => {
  vi.clearAllMocks();
  mocks.auth.sessionId = {};
  mocks.auth.user = { id: 1, role: "CEO", name: "CEO" };
  mocks.auth.hasPermission = () => true;
});
describe("data correctness", () => {
  it("CEO dashboard consumes numeric company totals even with no presentation cards", async () => {
    mocks.monthly.mockResolvedValue({
      completedCount: 0,
      completedGratisCount: 0,
      cancelledCount: 0,
    });
    mocks.report.mockResolvedValue({
      summary: [],
      metrics: {
        totalIncentive: 300,
        totalBottles: 2,
        totalJugs: 1,
        totalLiters: 4,
      },
    });
    const { result } = renderHook(() => useMonthlyDashboardStats("ceo"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(
      result.current.stats
        .find((x) => x.id === "incentive")
        ?.value.replace(/[^0-9]/g, ""),
    ).toBe("30000");
  });
  it("selector invalidates A immediately when selecting B, and clearing resets preselection", async () => {
    mocks.findById.mockImplementation((id: number) =>
      Promise.resolve(customer(id)),
    );
    const { result, rerender } = renderHook(
      ({ id }) => useCustomerSelector(id),
      { initialProps: { id: 1 as number | null } },
    );
    await waitFor(() => expect(result.current.selectedCustomer?.id).toBe(1));
    rerender({ id: 2 });
    expect(result.current.selectedCustomer).toBeNull();
    await waitFor(() => expect(result.current.selectedCustomer?.id).toBe(2));
    rerender({ id: null });
    expect(result.current.selectedCustomer).toBeNull();
    expect(result.current.isPreselected).toBe(false);
  });
  it("a late result from a previous session is ignored", async () => {
    const old = deferred<string>();
    const next = deferred<string>();
    const load = vi
      .fn()
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(next.promise);
    const { result, rerender } = renderHook(() =>
      useSessionRequest("same", load),
    );
    mocks.auth.sessionId = {};
    rerender();
    expect(result.current.data).toBeNull();
    await act(async () => {
      next.resolve("new");
    });
    await act(async () => {
      old.resolve("old");
    });
    expect(result.current.data).toBe("new");
  });
  it("reloading replaces the authoritative collection instead of keeping a removed requisition", async () => {
    const load = vi
      .fn()
      .mockResolvedValueOnce([{ id: 1 }])
      .mockResolvedValueOnce([]);
    const { result } = renderHook(() => useSessionRequest("monthly", load));
    await waitFor(() => expect(result.current.data).toEqual([{ id: 1 }]));
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.data).toEqual([]));
  });
  it("customer A form cannot be submitted under customer B route", async () => {
    const second = deferred<ReturnType<typeof customer>>();
    mocks.findById.mockImplementation((id: number) =>
      id === 1 ? Promise.resolve(customer(1)) : second.promise,
    );
    function Switch() {
      const navigate = useNavigate();
      return (
        <button onClick={() => navigate("/kupci/2/izmena")}>Switch</button>
      );
    }
    render(
      <MemoryRouter initialEntries={["/kupci/1/izmena"]}>
        <Switch />
        <Routes>
          <Route
            path="/kupci/:customerId/izmena"
            element={<NewCustomerPage />}
          />
        </Routes>
      </MemoryRouter>,
    );
    await screen.findByDisplayValue("Customer 1");
    fireEvent.click(screen.getByText("Switch"));
    expect(screen.queryByDisplayValue("Customer 1")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /izmene/ }),
    ).not.toBeInTheDocument();
    await act(async () => second.resolve(customer(2)));
    expect(await screen.findByDisplayValue("Customer 2")).toBeInTheDocument();
    expect(mocks.update).not.toHaveBeenCalled();
    const save = deferred<void>();
    mocks.update.mockReturnValue(save.promise);
    const button = screen.getByRole("button", { name: /izmene/ });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(mocks.update).toHaveBeenCalledTimes(1);
    expect(mocks.update).toHaveBeenCalledWith(
      2,
      expect.objectContaining({ objectName: "Customer 2" }),
    );
  });
  it("a saved employee followed by manager refresh failure does not report save failure", async () => {
    mocks.employees.mockResolvedValue([]);
    mocks.managers
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error("refresh unavailable"));
    mocks.create.mockResolvedValue({ id: 2 });
    render(
      <MemoryRouter initialEntries={["/?action=new"]}>
        <EmployeesPage />
      </MemoryRouter>,
    );
    await waitFor(() => expect(mocks.managers).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByText("Confirm employee"));
    await screen.findByText(/refresh unavailable/);
    expect(screen.queryByText("Confirm employee")).not.toBeInTheDocument();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it("employee creation follows current URL, including Back", async () => {
    mocks.employees.mockResolvedValue([]);
    mocks.managers.mockResolvedValue([]);
    function Back() {
      const navigate = useNavigate();
      return <button onClick={() => navigate(-1)}>Back</button>;
    }
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Back />
        <EmployeesPage />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByText("Zaposli radnika"));
    expect(screen.getByText("Confirm employee")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Back"));
    expect(screen.queryByText("Confirm employee")).not.toBeInTheDocument();
  });
  it("report years match backend limits and legacy subject links remain valid", () => {
    expect(
      readReportNavigation(
        new URLSearchParams("year=1999&salesRepId=12"),
        new Date(2026, 9, 1),
      ),
    ).toMatchObject({ year: 2026, salesRepId: 12, type: "monthly" });
  });
  it("employee access remains CEO-only", () => {
    mocks.auth.user.role = "BOOKKEEPER";
    render(
      <MemoryRouter>
        <EmployeesPage />
      </MemoryRouter>,
    );
    expect(screen.queryByText("Zaposli radnika")).not.toBeInTheDocument();
    expect(mocks.employees).not.toHaveBeenCalled();
  });
});
it("failed status confirmation stays open, retries, and prevents duplicate in-flight submissions", async () => {
  const pending = deferred<void>();
  const change = vi
    .fn()
    .mockReturnValueOnce(pending.promise)
    .mockResolvedValueOnce(undefined);
  render(
    <RequisitionStatusControl
      allowedTransitions={["approved"]}
      onStatusChange={change}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Promeni status" }));
  const confirm = screen.getByText("Da");
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect(change).toHaveBeenCalledTimes(1);
  await act(async () => pending.reject(new Error("try again")));
  expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  expect(screen.getByText("try again")).toBeInTheDocument();
  fireEvent.click(screen.getByText("Da"));
  await waitFor(() =>
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
  );
});
it("manager rejection is available and still requires a note", () => {
  const change = vi.fn().mockResolvedValue(undefined);
  render(
    <RequisitionStatusControl
      allowedTransitions={["approved", "rejected", "cancelled_by_manager"]}
      onStatusChange={change}
    />,
  );
  const status = screen.getByRole("combobox", { name: "Promeni status" });
  expect(screen.getByRole("option", { name: "Odbijeno" })).toBeInTheDocument();
  fireEvent.change(status, { target: { value: "rejected" } });
  fireEvent.click(screen.getByRole("button", { name: "Promeni status" }));
  expect(screen.getByRole("alert")).toHaveTextContent("Napomena je obavezna");
  expect(change).not.toHaveBeenCalled();
});
it("dialog traps focus, blocks Escape while saving and restores focus/scroll", () => {
  const close = vi.fn();
  function Dialog({ busy }: { busy: boolean }) {
    const ref = useDialog(close, busy);
    return (
      <div ref={ref} tabIndex={-1}>
        <button>First</button>
        <button>Last</button>
      </div>
    );
  }
  const trigger = document.createElement("button");
  document.body.append(trigger);
  trigger.focus();
  const { rerender, unmount } = render(<Dialog busy={true} />);
  expect(screen.getByText("First")).toHaveFocus();
  screen.getByText("Last").focus();
  fireEvent.keyDown(document, { key: "Tab" });
  expect(screen.getByText("First")).toHaveFocus();
  fireEvent.keyDown(document, { key: "Escape" });
  expect(close).not.toHaveBeenCalled();
  rerender(<Dialog busy={false} />);
  fireEvent.keyDown(document, { key: "Escape" });
  expect(close).toHaveBeenCalledTimes(1);
  unmount();
  expect(trigger).toHaveFocus();
  expect(document.body.style.overflow).toBe("");
  trigger.remove();
});

it("debounced searches hide stale matches and abort obsolete queries", async () => {
 vi.useFakeTimers();
 try {
  const old=deferred<{content: ReturnType<typeof customer>[]}>();mocks.findAll.mockReturnValueOnce(old.promise).mockResolvedValueOnce({content:[customer(2)]});
  const {result}=renderHook(()=>useCustomerSelector(null));act(()=>result.current.setCustomerSearch("old"));await act(async()=>{await vi.advanceTimersByTimeAsync(260)});expect(mocks.findAll).toHaveBeenCalledTimes(1);
  act(()=>result.current.setCustomerSearch("new"));expect(result.current.matchingCustomers).toEqual([]);await act(async()=>{await vi.advanceTimersByTimeAsync(260)});expect(result.current.matchingCustomers[0].id).toBe(2);await act(async()=>old.resolve({content:[customer(1)]}));expect(result.current.matchingCustomers[0].id).toBe(2);expect(mocks.findAll.mock.calls[0][3].aborted).toBe(true);
 } finally {vi.useRealTimers();}
});
