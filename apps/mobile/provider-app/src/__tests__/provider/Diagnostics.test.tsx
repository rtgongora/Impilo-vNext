/**
 * Diagnostics provider screen + service tests — order tracking / results inbox + offline-queued submit.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockQueueOnRetryable = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: (...a: unknown[]) => mockGet(...a), post: (...a: unknown[]) => mockPost(...a) },
}));

vi.mock("../../services/offlineClinicalQueue", () => ({
  queueClinicalCreateOnRetryableError: (...a: unknown[]) => mockQueueOnRetryable(...a),
}));

vi.mock("@impilo/mobile-offline", () => ({
  useSyncEngine: () => ({ pendingCount: 0 }),
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

describe("diagnosticsService reads", () => {
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

    expect(await fetchResultsInbox()).toEqual([]);
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/diagnostics/results-inbox");
  });

  it("fetchFulfilmentWorklist hits the category worklist endpoint", async () => {
    mockGet.mockResolvedValue({ data: { data: [{ orderId: "ORD-LAB", orderType: "LAB" }] } });
    const { fetchFulfilmentWorklist } = await import("../../services/diagnosticsService");

    const orders = await fetchFulfilmentWorklist("LAB");
    expect(orders).toHaveLength(1);
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/diagnostics/fulfilment-worklist?type=LAB");
  });
});

describe("diagnosticsService lab/procedure actions (mobile parity)", () => {
  beforeEach(() => {
    mockPost.mockReset();
    mockQueueOnRetryable.mockReset();
  });

  it("collectSpecimen posts to the order specimens endpoint", async () => {
    mockPost.mockResolvedValue({ data: { data: {} } });
    const { collectSpecimen } = await import("../../services/diagnosticsService");

    const res = await collectSpecimen("ORD-LAB", { specimenType: "blood" });
    expect(res.queuedOffline).toBe(false);
    expect(mockPost).toHaveBeenCalledWith(
      "/internal/v1/diagnostics/orders/ORD-LAB/specimens",
      expect.objectContaining({ specimenType: "blood" }),
    );
  });

  it("collectSpecimen offline-queues on retryable failure", async () => {
    mockPost.mockRejectedValue(new Error("network"));
    mockQueueOnRetryable.mockResolvedValue({ recordId: "local-9", queued: true });
    const { collectSpecimen } = await import("../../services/diagnosticsService");

    const res = await collectSpecimen("ORD-LAB");
    expect(res.queuedOffline).toBe(true);
    expect(mockQueueOnRetryable).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({ collection: "specimen_collections" }),
    );
  });

  it("specimenAction posts the lifecycle action", async () => {
    mockPost.mockResolvedValue({ data: {} });
    const { specimenAction } = await import("../../services/diagnosticsService");

    await specimenAction("SPC-1", "receive", { labNumber: "LAB-1" });
    expect(mockPost).toHaveBeenCalledWith(
      "/internal/v1/diagnostics/specimens/SPC-1/receive",
      { labNumber: "LAB-1" },
    );
  });

  it("transitionWorkflow drives the guarded transition", async () => {
    mockPost.mockResolvedValue({ data: {} });
    const { transitionWorkflow } = await import("../../services/diagnosticsService");

    await transitionWorkflow("ORD-LAB", "IN_PROGRESS", "analysing");
    expect(mockPost).toHaveBeenCalledWith(
      "/internal/v1/diagnostics/orders/ORD-LAB/workflow/transition",
      { target: "IN_PROGRESS", reason: "analysing" },
    );
  });

  it("acknowledgeCritical closes the critical loop from the field", async () => {
    mockPost.mockResolvedValue({ data: {} });
    const { acknowledgeCritical } = await import("../../services/diagnosticsService");

    await acknowledgeCritical("RES-1", "seen");
    expect(mockPost).toHaveBeenCalledWith(
      "/internal/v1/diagnostics/results/RES-1/critical/ack",
      { note: "seen" },
    );
  });
});

describe("submitDiagnosticOrder (offline-capable)", () => {
  beforeEach(() => {
    mockPost.mockReset();
    mockQueueOnRetryable.mockReset();
  });

  it("posts to the BFF when online", async () => {
    mockPost.mockResolvedValue({ data: { data: { orderId: "ORD-9", status: "DRAFT" } } });
    const { submitDiagnosticOrder } = await import("../../services/diagnosticsService");

    const result = await submitDiagnosticOrder({ orderType: "IMAGING", patientCpid: "CPID-1", items: [{ code: "CXR" }] });

    expect(result.queuedOffline).toBe(false);
    expect(result.order?.orderId).toBe("ORD-9");
    expect(mockPost).toHaveBeenCalledWith("/internal/v1/diagnostics/orders/draft", expect.objectContaining({
      orderType: "IMAGING", requestSource: "INTERNAL",
    }));
  });

  it("queues offline on a retryable failure", async () => {
    mockPost.mockRejectedValue(new Error("network"));
    mockQueueOnRetryable.mockResolvedValue({ recordId: "local-1", queued: true });
    const { submitDiagnosticOrder } = await import("../../services/diagnosticsService");

    const result = await submitDiagnosticOrder({ orderType: "IMAGING", patientCpid: "CPID-1", items: [{ code: "CXR" }] });

    expect(result.queuedOffline).toBe(true);
    expect(result.recordId).toBe("local-1");
    expect(mockQueueOnRetryable).toHaveBeenCalledWith(expect.any(Error), expect.objectContaining({
      collection: "diagnostic_orders", path: "/internal/v1/diagnostics/orders/draft",
    }));
  });

  it("rethrows when the failure is not retryable (not queued)", async () => {
    mockPost.mockRejectedValue(new Error("validation"));
    mockQueueOnRetryable.mockResolvedValue(null);
    const { submitDiagnosticOrder } = await import("../../services/diagnosticsService");

    await expect(submitDiagnosticOrder({ orderType: "LAB", patientCpid: "CPID-1", items: [{ code: "FBC" }] }))
      .rejects.toThrow("validation");
  });
});

describe("DiagnosticsScreen", () => {
  it("exports a function component that can be instantiated", async () => {
    const mod = await import("../../screens/provider/DiagnosticsScreen");
    expect(typeof mod.DiagnosticsScreen).toBe("function");
    expect(React.createElement(mod.DiagnosticsScreen).type).toBe(mod.DiagnosticsScreen);
  });
});
