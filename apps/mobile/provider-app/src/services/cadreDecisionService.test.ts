import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { resolveCadreDecision } from "./cadreDecisionService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { post: vi.fn() },
}));

describe("cadreDecisionService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it("posts the request and unwraps the cadre decision spine", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        data: {
          permittedWorkflows: ["OUTPATIENT"],
          cockpitSpine: [
            {
              tab: "ORDERS_RESULTS",
              enabled: true,
              actions: [{ action: "ORDER_LABS", enabled: true, requiresStepUp: false }],
            },
          ],
          escalation: { breakGlassAvailable: false, supervisorRequiredFor: ["PRESCRIBE"] },
          auditRef: "audit-1",
        },
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });

    const decision = await resolveCadreDecision({
      cadre: "CLINICIAN",
      visitType: "OUTPATIENT",
      encounterId: "enc-1",
    });

    expect(decision.cockpitSpine[0].tab).toBe("ORDERS_RESULTS");
    expect(decision.escalation.supervisorRequiredFor).toContain("PRESCRIBE");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/encounters/cadre-decision", {
      cadre: "CLINICIAN",
      visitType: "OUTPATIENT",
      encounterId: "enc-1",
    });
  });
});
