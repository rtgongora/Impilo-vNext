/**
 * financeService — citizen COSTA pending charges.
 *
 * Verifies fetchPendingCharges:
 *   1. Hits the live experience-bff route `/internal/v1/mobile/citizen/costa/charges/pending`
 *      (owner implied by the x-actor-id trust header — no id query param).
 *   2. Adapts the sovereign COSTA OutstandingBillDto rows onto the citizen UI PendingCharge
 *      shape, mapping only real fields and never fabricating service names/dates.
 *   3. Returns an unblocked result now the route is published.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mocks = vi.hoisted(() => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mocks.apiClient,
  ApiError: class ApiError extends Error {},
  fetchPage: vi.fn(),
}));

const mockApiClient = mocks.apiClient;

describe("financeService.fetchPendingCharges (citizen COSTA)", () => {
  beforeEach(() => {
    mockApiClient.get.mockReset();
  });

  it("hits the citizen COSTA route and adapts real outstanding bills", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          { billId: "BILL-1", facilityId: "f-1", encounterId: "ENC-9", balanceDue: 42.5, currency: "USD" },
          { billId: "BILL-2", patientPayable: 10, currency: "ZWG" },
        ],
      },
    });

    const { fetchPendingCharges } = await import("../../services/financeService");
    const result = await fetchPendingCharges();

    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/costa/charges/pending",
    );
    expect(result.blocked).toBe(false);
    expect(result.charges).toEqual([
      { id: "BILL-1", description: "Care episode ENC-9", amount: 42.5, currency: "USD", chargeDate: "", status: "PENDING" },
      { id: "BILL-2", description: "Outstanding bill", amount: 10, currency: "ZWG", chargeDate: "", status: "PENDING" },
    ]);
  });

  it("returns an empty unblocked result when the citizen owes nothing", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [] } });

    const { fetchPendingCharges } = await import("../../services/financeService");
    const result = await fetchPendingCharges();

    expect(result).toEqual({ charges: [], blocked: false });
  });
});
