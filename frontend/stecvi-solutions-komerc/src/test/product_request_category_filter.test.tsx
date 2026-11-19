import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";

import { ProductRequestForm } from "../shared/components/product_request/product_request_form";
import type { Product } from "../shared/types/product";

const products: Product[] = [
    {
        key: "B-1",
        code: "B-1",
        name: "Bottle product",
        price: 100,
        pictureUrl: "products/placeholder.jpg",
        packageType: "BOTTLE",
        active: true,
    },
    {
        key: "J-1",
        code: "J-1",
        name: "Jug product",
        price: 200,
        pictureUrl: "products/placeholder.jpg",
        packageType: "JUG",
        active: true,
    },
];

const formProps = {
    products,
    submitLabel: "Submit",
    confirmationTitle: "Confirm",
    confirmationDescription: "Confirm products",
    onCancel: vi.fn(),
    onConfirm: vi.fn().mockResolvedValue(undefined),
};

it("preserves quantities while product categories are switched locally", () => {
    const view = render(
        <ProductRequestForm {...formProps} visibleProductKeys={new Set(["B-1"])} />,
    );

    fireEvent.change(screen.getByLabelText("Količina za Bottle product"), {
        target: { value: "3" },
    });

    view.rerender(
        <ProductRequestForm {...formProps} visibleProductKeys={new Set(["J-1"])} />,
    );
    expect(screen.queryByLabelText("Količina za Bottle product")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Količina za Jug product"), {
        target: { value: "2" },
    });

    view.rerender(
        <ProductRequestForm {...formProps} visibleProductKeys={new Set(["B-1", "J-1"])} />,
    );
    expect(screen.getByLabelText("Količina za Bottle product")).toHaveValue(3);
    expect(screen.getByLabelText("Količina za Jug product")).toHaveValue(2);
});
