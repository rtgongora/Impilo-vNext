/**
 * CartScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("../../services/cartService", () => ({
  fetchCart: vi.fn().mockResolvedValue({ id: "cart-1", items: [], totalItems: 0, totalAmount: 0, currency: "ZAR" }),
  removeFromCart: vi.fn().mockResolvedValue({}),
  checkout: vi.fn().mockResolvedValue({}),
}));

describe("CartScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/marketplace/CartScreen");
    expect(typeof mod.CartScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/marketplace/CartScreen");
    const element = React.createElement(mod.CartScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.CartScreen);
  });
});
