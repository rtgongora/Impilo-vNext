/**
 * FinanceOverviewScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", () => ({
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

vi.mock("../../services/financeService", () => ({
  fetchRevenueSummary: vi.fn().mockResolvedValue({
    todayRevenue: 0,
    weekTotal: 0,
    monthTotal: 0,
    currency: "ZAR",
  }),
  fetchPendingClaims: vi.fn().mockResolvedValue([]),
  fetchRecentPayments: vi.fn().mockResolvedValue([]),
}));

describe("FinanceOverviewScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/FinanceOverviewScreen");
    expect(typeof mod.FinanceOverviewScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/FinanceOverviewScreen");
    const element = React.createElement(mod.FinanceOverviewScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.FinanceOverviewScreen);
  });
});
