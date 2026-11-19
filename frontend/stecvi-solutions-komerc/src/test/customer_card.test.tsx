import { render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { CustomerCard } from "../features/customer/components/customer_card";

const auth = vi.hoisted(() => ({
  user: { role: "MANAGER" },
  hasPermission: vi.fn(() => true),
}));

vi.mock("../features/auth/hooks/useAuth", () => ({ useAuth: () => auth }));

const customer = {
  id: 1,
  salesRep: null,
  actions: {
    canCreateRequisition: false,
    canViewRequisitionHistory: true,
    canChangeSalesRep: false,
  },
  objectName: "Test customer",
  companyName: "Test company",
  pib: "123456789",
  phone: "+381 60 1234567",
  email: "customer@example.test",
  address: "Test address",
  googleMapsUrl: "",
  note: "",
  status: "approved" as const,
};

const actions = {
  onOpen: vi.fn(),
  onOrder: vi.fn(),
  onRequisitionHistory: vi.fn(),
  onChangeSalesRep: vi.fn(),
};

beforeEach(() => {
  auth.user = { role: "MANAGER" };
  auth.hasPermission.mockReturnValue(true);
});

it("does not show the call action to a manager", () => {
  render(<CustomerCard customer={customer} {...actions} />);

  expect(screen.queryByRole("link", { name: "Pozovi" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Pozovi" })).not.toBeInTheDocument();
});

it("keeps the call action for a sales representative with permission", () => {
  auth.user = { role: "SALES_REP" };
  render(<CustomerCard customer={customer} {...actions} />);

  expect(screen.getByRole("link", { name: "Pozovi" })).toHaveAttribute(
    "href",
    "tel:+381601234567",
  );
});
