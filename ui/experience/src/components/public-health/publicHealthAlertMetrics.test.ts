import { describe, expect, it } from "vitest";
import type { PublicHealthAlert } from "@/hooks/queries/usePublicHealth";
import { countHighOrCriticalSurveillanceAlerts, countOpenSurveillanceAlerts } from "./publicHealthAlertMetrics";

function alert(partial: Partial<PublicHealthAlert> & Pick<PublicHealthAlert, "id">): PublicHealthAlert {
  return {
    title: "t",
    severity: "medium",
    location: "—",
    status: "active",
    detectedAt: "",
    ...partial,
  };
}

describe("countOpenSurveillanceAlerts", () => {
  it("excludes acknowledged", () => {
    const list = [
      alert({ id: "1", status: "active" }),
      alert({ id: "2", status: "acknowledged" }),
    ];
    expect(countOpenSurveillanceAlerts(list)).toBe(1);
  });
});

describe("countHighOrCriticalSurveillanceAlerts", () => {
  it("counts high and critical severities", () => {
    const list = [
      alert({ id: "1", severity: "high" }),
      alert({ id: "2", severity: "CRITICAL" }),
      alert({ id: "3", severity: "low" }),
    ];
    expect(countHighOrCriticalSurveillanceAlerts(list)).toBe(2);
  });
});
