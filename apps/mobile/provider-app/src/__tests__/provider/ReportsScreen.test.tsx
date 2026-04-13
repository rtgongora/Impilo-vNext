/**
 * ReportsScreen Tests — Verifies export and basic instantiation.
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

vi.mock("../../services/reportService", () => ({
  fetchReportList: vi.fn().mockResolvedValue([]),
  fetchReportData: vi.fn().mockResolvedValue({
    reportName: "Test Report",
    generatedAt: new Date().toISOString(),
    entries: [],
  }),
}));

describe("ReportsScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/ReportsScreen");
    expect(typeof mod.ReportsScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/ReportsScreen");
    const element = React.createElement(mod.ReportsScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.ReportsScreen);
  });
});
