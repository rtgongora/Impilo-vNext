import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { getProfessionalAlerts } from "./professionalAlertsService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

describe("professionalAlertsService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it("calls the same BFF surface web /professional uses and unwraps attributes", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        data: {
          id: "actor-1",
          type: "professional-alerts",
          attributes: {
            status: "OK",
            items: [
              {
                id: "licence:l1",
                kind: "LICENCE_ALERT",
                source: "varapi",
                title: "Nursing licence — Nurses Council",
                description: "Expires in 21 days",
                priority: "HIGH",
                due_at: "2026-08-18T00:00:00Z",
                href: "/my-professional/licence",
              },
            ],
            summary: { total: 1 },
            note: null,
          },
        },
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const result = await getProfessionalAlerts();
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/professional/alerts");
    expect(result.status).toBe("OK");
    expect(result.items).toHaveLength(1);
    expect(result.items[0].kind).toBe("LICENCE_ALERT");
  });

  it("passes a DEGRADED status through rather than flattening it to an empty list", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        data: {
          id: "actor-1",
          type: "professional-alerts",
          attributes: {
            status: "DEGRADED",
            items: [],
            summary: { total: 0 },
            note: "VARAPI did not answer in time",
          },
        },
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const result = await getProfessionalAlerts();
    expect(result.status).toBe("DEGRADED");
    expect(result.note).toBe("VARAPI did not answer in time");
  });

  it("propagates a thrown request error to the caller (never a silent all-clear)", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error("network down"));
    await expect(getProfessionalAlerts()).rejects.toThrow("network down");
  });
});
