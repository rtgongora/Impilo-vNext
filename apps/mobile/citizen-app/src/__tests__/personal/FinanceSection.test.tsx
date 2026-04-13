/**
 * FinanceSection Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("../../services/financeService", () => ({
  fetchBalance: vi.fn().mockResolvedValue({ amount: 0, currency: "ZAR" }),
  fetchPendingCharges: vi.fn().mockResolvedValue([]),
  fetchTransactions: vi.fn().mockResolvedValue([]),
}));

describe("FinanceSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/FinanceSection");
    expect(typeof mod.FinanceSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/FinanceSection");
    const element = React.createElement(mod.FinanceSection);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.FinanceSection);
  });
});
