import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { applicationNavigation } from "../app/navigation/application_navigation";
import { canAccessNavigationItem } from "../app/navigation/navigation_access";
const state = vi.hoisted(() => ({
  auth: { user: { role: "SALES_REP" }, sessionId: {}, hasPermission:()=>true },
  detail: vi.fn(),
  activities: vi.fn(),
  activity: vi.fn(),
  summary: vi.fn(),
  list: vi.fn(),
}));
vi.mock("../features/auth/hooks/useAuth", () => ({
  useAuth: () => state.auth,
}));
vi.mock("../features/ai/risk_api", () => ({ riskApi: state }));
vi.mock("../app/api/http_client", () => ({
  apiRequest: vi.fn(async () => []),
}));
vi.mock("../app/layouts/app_layout", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
import {
  CustomerAiAnalysis,
  RiskBadge,
  RiskOverview,
} from "../features/ai/risk_components";
import AiPage from "../features/ai/ai_page";
const assessment = (id: number) => ({
  customerId: id,
  customerName: `Customer ${id}`,
  level: "INSUFFICIENT_DATA",
  status: "MODEL_UNAVAILABLE",
  at: "2026-10-23T12:00:00Z",
  modelVersion: null,
  features: null,
  factors: [],
  recommendation: "Prikupite istoriju",
  stale: false,
});
beforeEach(() => {
  vi.clearAllMocks();
  state.auth = { user: { role: "SALES_REP" }, sessionId: {}, hasPermission:()=>true };
  state.detail.mockImplementation(async (id: number) => assessment(id));
  state.activities.mockResolvedValue([]);
});
it("badges distinguish missing data from actual high risk", () => {
  render(
    <>
      <RiskBadge level="HIGH" />
      <RiskBadge level="INSUFFICIENT_DATA" />
    </>,
  );
  expect(screen.getByText("Visok rizik")).toBeInTheDocument();
  expect(screen.getByText("Nedovoljno podataka")).toBeInTheDocument();
});
it("missing model has an explicit state without a confidence percentage", async () => {
  render(<CustomerAiAnalysis customerId={1} />);
  await screen.findByText("Status: MODEL_UNAVAILABLE");
  expect(screen.getByText(/Nema aktivnog modela/)).toBeInTheDocument();
  expect(screen.queryByText(/\d+%/)).not.toBeInTheDocument();
});
it("late customer results cannot replace another customer", async () => {
  let finish!: (value: unknown) => void;
  state.detail.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const view = render(<CustomerAiAnalysis customerId={1} />);
  view.rerender(<CustomerAiAnalysis customerId={2} />);
  await screen.findByText("Status: MODEL_UNAVAILABLE");
  await act(async () => finish({ ...assessment(1), status: "OLD_CUSTOMER" }));
  expect(screen.queryByText(/OLD_CUSTOMER/)).not.toBeInTheDocument();
});
it("session changes discard unsaved activities", async () => {
  const view = render(<CustomerAiAnalysis customerId={1} />);
  await screen.findByLabelText("Ishod kontakta");
  fireEvent.change(screen.getByLabelText("Ishod kontakta"), {
    target: { value: "Old account notes" },
  });
  state.auth = { ...state.auth, sessionId: {}, hasPermission:()=>true };
  view.rerender(<CustomerAiAnalysis customerId={1} />);
  await waitFor(() =>
    expect(screen.getByLabelText("Ishod kontakta")).toHaveValue(""),
  );
});
it("activity writes reject duplicate clicks and show the saved outcome after reload", async () => {
  let finish!: () => void;
  state.activity.mockImplementation(
    () =>
      new Promise<void>((resolve) => {
        finish = resolve;
      }),
  );
  render(<CustomerAiAnalysis customerId={1} />);
  await screen.findByLabelText("Ishod kontakta");
  fireEvent.change(screen.getByLabelText("Ishod kontakta"), {
    target: { value: "Customer contacted" },
  });
  const button = screen.getByRole("button", { name: "Evidentiraj kontakt" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(state.activity).toHaveBeenCalledTimes(1);
  state.activities.mockResolvedValue([
    {
      id: 1,
      type: "CONTACT",
      at: "2026-10-23T12:00:00Z",
      outcome: "Customer contacted",
    },
  ]);
  await act(async () => finish());
  await screen.findByText(/CONTACT: Customer contacted/);
});
it("overview shows retryable errors", async () => {
  state.summary.mockRejectedValue(new Error("Service unavailable"));
  render(
    <MemoryRouter>
      <RiskOverview />
    </MemoryRouter>,
  );
  await screen.findByRole("alert");
  expect(screen.getByText(/Service unavailable/)).toBeInTheDocument();
});
it("bookkeepers cannot see AI navigation page or request data", () => {
  state.auth = { ...state.auth, user: { role: "BOOKKEEPER" } };
  render(
    <MemoryRouter>
      <AiPage />
    </MemoryRouter>,
  );
  expect(state.list).not.toHaveBeenCalled();
});
it("risk filters are sent to the server and customer links open existing screens", async () => {
  state.summary.mockResolvedValue({
    total: 1,
    distribution: {},
    increased: 0,
    highRiskPercentage: null,
    historicalHighRiskLiters: 0,
    teams: [],
  });
  state.list.mockResolvedValue({
    content: [assessment(1)],
    total: 1,
    page: 0,
    size: 25,
  });
  render(
    <MemoryRouter>
      <AiPage />
    </MemoryRouter>,
  );
  await screen.findByText("Customer 1");
  expect(screen.getByRole("link", { name: "Customer 1" })).toHaveAttribute(
    "href",
    "/kupci?search=Customer%201&customerId=1",
  );
  fireEvent.change(screen.getByLabelText("Rizik"), {
    target: { value: "HIGH" },
  });
  await waitFor(() =>
    expect(state.list).toHaveBeenLastCalledWith(
      "risk=HIGH",
      expect.any(AbortSignal),
    ),
  );
  const requestsBeforeTyping = state.list.mock.calls.length;
  fireEvent.change(screen.getByRole("searchbox"), { target: { value: "  Novi kupac  " } });
  expect(state.list).toHaveBeenCalledTimes(requestsBeforeTyping);
  fireEvent.click(screen.getByRole("button", { name: "Pretraži" }));
  await waitFor(() => {
    const parameters = new URLSearchParams(state.list.mock.lastCall?.[0]);
    expect(parameters.get("search")).toBe("Novi kupac");
    expect(parameters.get("risk")).toBe("HIGH");
    expect(parameters.has("page")).toBe(false);
  });
});

it("AI application navigation preserves the page's role and permission restrictions", () => {
  const item = applicationNavigation.find((entry) => entry.id === "ai-analytics");
  expect(item?.route).toBe("/ai-analitika");
  if (!item) throw new Error("AI navigation entry is missing");
  for (const role of ["SALES_REP", "MANAGER", "CEO"] as const) {
    expect(canAccessNavigationItem(item, { role }, () => true)).toBe(true);
    expect(canAccessNavigationItem(item, { role }, () => false)).toBe(false);
  }
  expect(canAccessNavigationItem(item, { role: "BOOKKEEPER" }, () => true)).toBe(false);
});
