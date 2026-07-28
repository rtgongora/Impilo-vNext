import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { listResolvedWorkContexts, mintWorkContextSession } from "./workContextService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

describe("workContextService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("listResolvedWorkContexts unwraps the JSON:API attributes", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        data: {
          id: "actor-1",
          type: "work-context-resolved",
          attributes: {
            contexts: [
              {
                contextId: "ctx-1",
                contextKind: "facility",
                sourceSystem: "VASHANDI",
                facilityId: "fac-1",
                availableModes: ["CLINICAL_CARE"],
                defaultMode: "CLINICAL_CARE",
                restrictions: [],
                label: "Harare Central",
                groupHint: "today",
              },
            ],
            sourceStatuses: [{ system: "VASHANDI", state: "LIVE" }],
            recommendedContextId: "ctx-1",
            requiresContextChooser: false,
          },
        },
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const result = await listResolvedWorkContexts();
    expect(result.contexts).toHaveLength(1);
    expect(result.contexts[0].contextId).toBe("ctx-1");
    expect(result.recommendedContextId).toBe("ctx-1");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/work-context/resolved");
  });

  it("mintWorkContextSession posts contextId+workMode and unwraps the minted token", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        data: {
          id: "actor-1",
          type: "work-mode-session",
          attributes: {
            token: "wct-jwt",
            jti: "jti-1",
            expiresAt: "2026-07-28T12:15:00Z",
            contextId: "ctx-1",
            workMode: "CLINICAL_CARE",
          },
        },
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const result = await mintWorkContextSession("ctx-1", "CLINICAL_CARE");
    expect(result.token).toBe("wct-jwt");
    expect(result.jti).toBe("jti-1");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/work-context/session/mode", {
      contextId: "ctx-1",
      workMode: "CLINICAL_CARE",
    });
  });

  it("mintWorkContextSession includes previousJti when supplied", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: "a1", type: "work-mode-session", attributes: {} } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    await mintWorkContextSession("ctx-2", "FACILITY_MANAGEMENT", "old-jti");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/work-context/session/mode", {
      contextId: "ctx-2",
      workMode: "FACILITY_MANAGEMENT",
      previousJti: "old-jti",
    });
  });
});
