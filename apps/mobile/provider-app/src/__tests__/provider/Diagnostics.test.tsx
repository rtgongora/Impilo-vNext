/**
 * Diagnostics provider screen + service tests — order tracking / results inbox data loading.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";

const mockGet = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: (...args: unknown[]) => mockGet(...args) },
}));

vi.mock("@impilo/mobile-design-system", () => ({
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Button: () => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
}));

describe("diagnosticsService", () => {
  beforeEach(() => mockGet.mockReset());

  it("fetchDiagnosticOrders calls the diagnostics orders endpoint with type filter", async () => {
    mockGet.mockResolvedValue({ data: { data: [{ orderId: "ORD-1", status: "PLACED" }] } });
    const { fetchDiagnosticOrders } = await import("../../services/diagnosticsService");

    const orders = await fetchDiagnosticOrders({ type: "IMAGING" });

    expect(orders).toHaveLength(1);
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/diagnostics/orders?type=IMAGING");
  });

  it("fetchResultsInbox calls the results-inbox endpoint", async () => {
    mockGet.mockResolvedValue({ data: { data: [] } });
    const { fetchResultsInbox } = await import("../../services/diagnosticsService");

    const inbox = await fetchResultsInbox();

    expect(inbox).toEqual([]);
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/diagnostics/results-inbox");
  });
});

describe("DiagnosticsScreen", () => {
  it("exports a function component that can be instantiated", async () => {
    const mod = await import("../../screens/provider/DiagnosticsScreen");
    expect(typeof mod.DiagnosticsScreen).toBe("function");
    const element = React.createElement(mod.DiagnosticsScreen);
    expect(element.type).toBe(mod.DiagnosticsScreen);
  });
});
