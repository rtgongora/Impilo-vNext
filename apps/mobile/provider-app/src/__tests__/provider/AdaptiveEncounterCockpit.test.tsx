/**
 * AdaptiveEncounterCockpit (mobile) Tests — export + basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("../../services/cadreDecisionService", () => ({
  resolveCadreDecision: vi.fn().mockResolvedValue({
    permittedWorkflows: ["OUTPATIENT"],
    cockpitSpine: [
      { tab: "ORDERS_RESULTS", enabled: true, actions: [{ action: "ORDER_LABS", enabled: true, requiresStepUp: false }] },
    ],
    escalation: { breakGlassAvailable: false, supervisorRequiredFor: [] },
    auditRef: "audit-1",
  }),
}));

describe("AdaptiveEncounterCockpit (mobile)", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/AdaptiveEncounterCockpit");
    expect(typeof mod.AdaptiveEncounterCockpit).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/AdaptiveEncounterCockpit");
    const element = React.createElement(mod.AdaptiveEncounterCockpit, { request: { cadre: "CLINICIAN" } });
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.AdaptiveEncounterCockpit);
  });
});
