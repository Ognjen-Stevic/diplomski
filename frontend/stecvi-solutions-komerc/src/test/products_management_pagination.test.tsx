import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

const state = vi.hoisted(() => ({
  catalog: {
    page: {
      content: [{
        id: 16,
        name: "Product 16",
        code: "P-016",
        pictureUrl: "products/placeholder.jpg",
        price: 100,
        volumeLiters: 0.7,
        stimulationPerUnit: 5,
        managerStimulationPerUnit: 2,
        active: true,
      }],
      totalElements: 16,
      totalPages: 2,
      pageNumber: 1,
    },
    products: [] as Array<{ id: number; name: string; active: boolean }>,
    loading: false,
    error: "",
    retry: vi.fn(),
  },
  useCatalog: vi.fn(),
  deactivate: vi.fn(),
}));

state.catalog.products = state.catalog.page.content;

vi.mock("../features/auth/hooks/useAuth", () => ({
  useAuth: () => ({ user: { role: "CEO" } }),
}));
vi.mock("../shared/hooks/use_session_request", () => ({
  useSessionCurrent: () => () => true,
}));
vi.mock("../features/product/hooks/use_product_catalog", () => ({
  useProductCatalog: (...arguments_: unknown[]) => state.useCatalog(...arguments_),
}));
vi.mock("../features/product/product_management_api", () => ({
  productManagementApi: {
    create: vi.fn(),
    deactivate: (...arguments_: unknown[]) => state.deactivate(...arguments_),
  },
}));
vi.mock("../app/layouts/app_layout", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock("../features/product/components/product_management_card", () => ({
  ProductManagementCard: ({ product, onDeactivate }: {
    product: { name: string };
    onDeactivate: () => void;
  }) => <button onClick={onDeactivate}>{product.name}</button>,
}));
vi.mock("../features/product/components/product_deactivation_confirmation", () => ({
  ProductDeactivationConfirmation: ({ onConfirm }: { onConfirm: () => void }) => (
    <button onClick={onConfirm}>Confirm deactivation</button>
  ),
}));
vi.mock("../features/product/components/product_form", () => ({ ProductForm: () => null }));

import ProductsManagementPage from "../features/product/pages/products_management_page";

beforeEach(() => {
  vi.clearAllMocks();
  state.catalog.page = { ...state.catalog.page, totalPages: 2, pageNumber: 1 };
  state.catalog.products = state.catalog.page.content;
  state.catalog.loading = false;
  state.catalog.error = "";
  state.useCatalog.mockImplementation(() => state.catalog);
  state.deactivate.mockResolvedValue({ ...state.catalog.page.content[0], active: false });
});

it("navigates server pages and resets to page one when filters change", async () => {
  render(
    <MemoryRouter initialEntries={["/products?page=2"]}>
      <ProductsManagementPage />
    </MemoryRouter>,
  );

  expect(state.useCatalog).toHaveBeenLastCalledWith(1, "", "ALL", true);
  fireEvent.change(screen.getByLabelText("Filtriraj po statusu"), {
    target: { value: "ACTIVE" },
  });
  await waitFor(() =>
    expect(state.useCatalog).toHaveBeenLastCalledWith(0, "", "ACTIVE", true),
  );

  fireEvent.change(screen.getByRole("searchbox"), { target: { value: "rakija" } });
  await waitFor(() =>
    expect(state.useCatalog).toHaveBeenLastCalledWith(0, "rakija", "ACTIVE", true),
  );
});

it("reloads the current server page after deactivation", async () => {
  render(
    <MemoryRouter initialEntries={["/products"]}>
      <ProductsManagementPage />
    </MemoryRouter>,
  );

  fireEvent.click(screen.getByRole("button", { name: "Product 16" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm deactivation" }));

  await waitFor(() => expect(state.deactivate).toHaveBeenCalledWith(16));
  expect(state.catalog.retry).toHaveBeenCalled();
});

it("redirects an out-of-range request to the last available page", async () => {
  state.catalog.page = { ...state.catalog.page, totalPages: 2, pageNumber: 5 };

  render(
    <MemoryRouter initialEntries={["/products?page=6"]}>
      <ProductsManagementPage />
    </MemoryRouter>,
  );

  expect(state.useCatalog).toHaveBeenCalledWith(5, "", "ALL", true);
  await waitFor(() =>
    expect(state.useCatalog).toHaveBeenLastCalledWith(1, "", "ALL", true),
  );
});
